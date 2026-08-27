from __future__ import annotations

import secrets

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from redis import Redis

from app.config import get_settings


basic_auth = HTTPBasic(auto_error=False)


def require_admin(
    request: Request,
    credentials: HTTPBasicCredentials | None = Depends(basic_auth),
) -> str:
    enforce_rate_limit(request)
    settings = get_settings()
    if not settings.admin_username or not settings.admin_password:
        raise HTTPException(status_code=503, detail="Admin access is not configured")
    valid = credentials is not None and secrets.compare_digest(
        credentials.username.encode("utf-8"), settings.admin_username.encode("utf-8")
    ) and secrets.compare_digest(
        credentials.password.encode("utf-8"), settings.admin_password.encode("utf-8")
    )
    if not valid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid administrator credentials",
            headers={"WWW-Authenticate": "Basic"},
        )
    return settings.admin_username


def enforce_rate_limit(request: Request) -> None:
    settings = get_settings()
    client = request.client.host if request.client else "unknown"
    redis: Redis = request.app.state.redis
    key = f"rate:{client}:{request.url.path}"
    try:
        current = redis.incr(key)
        if current == 1:
            redis.expire(key, settings.rate_limit_window_seconds)
        if current > settings.rate_limit_requests:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many requests. Try again later.",
                headers={"Retry-After": str(settings.rate_limit_window_seconds)},
            )
    except HTTPException:
        raise
    except Exception:
        # Health reporting exposes a Redis outage. Core analysis remains available.
        return
