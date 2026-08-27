from __future__ import annotations

import hashlib
import secrets

import httpx
from fastapi import APIRouter, Depends, HTTPException, Request, Response, status
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth import bearer, current_user, hash_password, issue_access_token, normalize_identifier, verify_password
from app.database import get_db
from app.models import AccessToken, AnalysisOwnership, AuditEvent, ExternalIdentity, User
from app.config import get_settings
from app.schemas import DeleteAccountRequest, LoginRequest, RegisterRequest, TokenResponse, UserResponse, YandexLoginRequest
from app.security import enforce_rate_limit


router = APIRouter(prefix="/api/v1/auth", tags=["auth"])


@router.post("/yandex", response_model=TokenResponse)
async def yandex_login(
    payload: YandexLoginRequest,
    request: Request,
    db: Session = Depends(get_db),
) -> TokenResponse:
    """Verify a Yandex OAuth token server-side and issue a FAITH session."""
    enforce_rate_limit(request)
    client_id = get_settings().yandex_client_id
    if not client_id:
        raise HTTPException(status_code=503, detail="Yandex OAuth is not configured")

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                "https://login.yandex.ru/info",
                params={"format": "json"},
                headers={"Authorization": f"OAuth {payload.oauth_token}"},
            )
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail="Yandex ID is unavailable") from exc
    if response.status_code != 200:
        raise HTTPException(status_code=401, detail="Invalid Yandex token")

    profile = response.json()
    if profile.get("client_id") != client_id or not profile.get("id"):
        raise HTTPException(status_code=401, detail="Yandex token was issued for another application")

    subject = str(profile["id"])
    identity = db.scalar(
        select(ExternalIdentity).where(
            ExternalIdentity.provider == "yandex",
            ExternalIdentity.provider_subject == subject,
        )
    )
    user = db.get(User, identity.user_id) if identity is not None else None
    if user is None:
        email_value = profile.get("default_email")
        email = email_value.strip().lower() if isinstance(email_value, str) and email_value.strip() else None
        if email and db.scalar(select(User).where(User.email == email)) is not None:
            raise HTTPException(status_code=409, detail="An account with this email already exists")
        user = User(email=email, phone=None, password_hash=hash_password(secrets.token_urlsafe(32)))
        db.add(user)
        db.flush()
        db.add(ExternalIdentity(user_id=user.id, provider="yandex", provider_subject=subject))

    if not user.is_active:
        raise HTTPException(status_code=401, detail="Account is disabled")
    token, ttl = issue_access_token(db, user)
    db.add(AuditEvent(action="auth.yandex", outcome="success", resource_id=str(user.id)))
    db.commit()
    db.refresh(user)
    return TokenResponse(access_token=token, expires_in=ttl, user=UserResponse.model_validate(user))


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def register(
    payload: RegisterRequest,
    request: Request,
    db: Session = Depends(get_db),
) -> TokenResponse:
    enforce_rate_limit(request)
    try:
        email, phone = normalize_identifier(payload.identifier)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    duplicate_query = select(User).where(User.email == email) if email else select(User).where(User.phone == phone)
    if db.scalar(duplicate_query) is not None:
        raise HTTPException(status_code=409, detail="Account already exists")
    user = User(email=email, phone=phone, password_hash=hash_password(payload.password))
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
        email, phone = normalize_identifier(payload.identifier)
    except ValueError:
        email, phone = None, None
    query = select(User).where(User.email == email) if email else select(User).where(User.phone == phone)
    user = db.scalar(query) if email or phone else None
    if user is None or not user.is_active or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")
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


@router.delete(
    "/account",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def delete_account(
    payload: DeleteAccountRequest,
    request: Request,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> Response:
    """Permanently remove an account after password or OAuth-session verification."""
    enforce_rate_limit(request)
    has_external_identity = db.scalar(
        select(ExternalIdentity.id).where(ExternalIdentity.user_id == user.id).limit(1)
    ) is not None
    if not has_external_identity and (
        payload.password is None or not verify_password(payload.password, user.password_hash)
    ):
        raise HTTPException(status_code=401, detail="Invalid password")

    # Explicit deletes make the privacy behavior independent of ORM cascade
    # configuration; PostgreSQL foreign keys remain a second line of defence.
    for model in (AccessToken, ExternalIdentity, AnalysisOwnership):
        for record in db.scalars(select(model).where(model.user_id == user.id)):
            db.delete(record)
    db.delete(user)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
