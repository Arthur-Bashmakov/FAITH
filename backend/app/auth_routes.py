from __future__ import annotations

import hashlib

from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import bearer, current_user, hash_password, issue_access_token, normalize_email, verify_password
from app.database import get_db
from app.models import AccessToken, AuditEvent, User
from app.schemas import LoginRequest, RegisterRequest, TokenResponse, UserResponse
from app.security import enforce_rate_limit


router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def register(
    payload: RegisterRequest,
    request: Request,
    db: Session = Depends(get_db),
) -> TokenResponse:
    enforce_rate_limit(request)
    try:
        email = normalize_email(payload.email)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    if db.scalar(select(User).where(User.email == email)) is not None:
        raise HTTPException(status_code=409, detail="Account already exists")
    user = User(email=email, password_hash=hash_password(payload.password))
    db.add(user)
    db.flush()
    token, ttl = issue_access_token(db, user)
    db.add(AuditEvent(action="auth.register", outcome="success", resource_id=str(user.id)))
    db.commit()
    db.refresh(user)
    return TokenResponse(access_token=token, expires_in=ttl, user=UserResponse.model_validate(user))


@router.post("/login", response_model=TokenResponse)
def login(
    payload: LoginRequest,
    request: Request,
    db: Session = Depends(get_db),
) -> TokenResponse:
    enforce_rate_limit(request)
    try:
        email = normalize_email(payload.email)
    except ValueError:
        email = "invalid@example.invalid"
    user = db.scalar(select(User).where(User.email == email))
    if user is None or not user.is_active or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    token, ttl = issue_access_token(db, user)
    db.add(AuditEvent(action="auth.login", outcome="success", resource_id=str(user.id)))
    db.commit()
    return TokenResponse(access_token=token, expires_in=ttl, user=UserResponse.model_validate(user))


@router.get("/me", response_model=UserResponse)
def me(user: User = Depends(current_user)) -> User:
    return user


@router.post(
    "/logout",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def logout(
    credentials: HTTPAuthorizationCredentials = Depends(bearer),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> Response:
    token_hash = hashlib.sha256(credentials.credentials.encode("utf-8")).hexdigest()
    token = db.scalar(select(AccessToken).where(AccessToken.token_hash == token_hash))
    if token is not None:
        db.delete(token)
    db.add(AuditEvent(action="auth.logout", outcome="success", resource_id=str(user.id)))
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
