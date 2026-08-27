from __future__ import annotations

from datetime import datetime, timedelta, timezone
import base64
import hashlib
import hmac
import secrets

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.models import AccessToken, User


bearer = HTTPBearer(auto_error=False)
PBKDF2_ITERATIONS = 600_000


def normalize_email(email: str) -> str:
    value = email.strip().casefold()
    if value.count("@") != 1 or value.startswith("@") or value.endswith("@") or "." not in value.split("@", 1)[1]:
        raise ValueError("Invalid email address")
    return value


def normalize_phone(phone: str) -> str:
    value = phone.strip().replace(" ", "").replace("(", "").replace(")", "").replace("-", "")
    if value.startswith("8") and len(value) == 11:
        value = "+7" + value[1:]
    elif value.startswith("00"):
        value = "+" + value[2:]
    if not value.startswith("+") or not value[1:].isdigit() or not 10 <= len(value[1:]) <= 15:
        raise ValueError("Invalid phone number")
    return value


def normalize_identifier(identifier: str) -> tuple[str | None, str | None]:
    if "@" in identifier:
        return normalize_email(identifier), None
    return None, normalize_phone(identifier)


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, PBKDF2_ITERATIONS)
    return "pbkdf2_sha256${}${}${}".format(
        PBKDF2_ITERATIONS,
        base64.urlsafe_b64encode(salt).decode("ascii"),
        base64.urlsafe_b64encode(digest).decode("ascii"),
    )


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, iterations, salt, expected = encoded.split("$", 3)
        if algorithm != "pbkdf2_sha256":
            return False
        digest = hashlib.pbkdf2_hmac(
            "sha256",
            password.encode("utf-8"),
            base64.urlsafe_b64decode(salt),
            int(iterations),
        )
        return hmac.compare_digest(base64.urlsafe_b64encode(digest).decode("ascii"), expected)
    except (ValueError, TypeError):
        return False


def issue_access_token(db: Session, user: User) -> tuple[str, int]:
    ttl = get_settings().access_token_ttl_seconds
    raw = secrets.token_urlsafe(32)
    db.add(
        AccessToken(
            user_id=user.id,
            token_hash=hashlib.sha256(raw.encode("utf-8")).hexdigest(),
            expires_at=datetime.now(timezone.utc) + timedelta(seconds=ttl),
        )
    )
    return raw, ttl


def request_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User | None:
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Authentication required",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if credentials is None:
        if get_settings().auth_required:
            raise unauthorized
        return None
    token_hash = hashlib.sha256(credentials.credentials.encode("utf-8")).hexdigest()
    record = db.scalar(select(AccessToken).where(AccessToken.token_hash == token_hash))
    if record is None or record.expires_at <= datetime.now(timezone.utc):
        raise unauthorized
    user = db.get(User, record.user_id)
    if user is None or not user.is_active:
        raise unauthorized
    return user


def current_user(user: User | None = Depends(request_user)) -> User:
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user
