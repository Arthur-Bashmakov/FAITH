from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class HealthResponse(BaseModel):
    status: str
    database: str
    redis: str


class AnalysisResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    file_name: str
    verdict: str
    synthetic_probability: float = Field(ge=0.0, le=1.0)
    model_version: str
    created_at: datetime
    cached: bool = False


class RegisterRequest(BaseModel):
    identifier: str = Field(min_length=5, max_length=254)
    password: str = Field(min_length=10, max_length=128)


class LoginRequest(BaseModel):
    identifier: str = Field(min_length=5, max_length=254)
    password: str = Field(min_length=1, max_length=128)


class YandexLoginRequest(BaseModel):
    oauth_token: str = Field(min_length=20, max_length=4096)


class DeleteAccountRequest(BaseModel):
    password: str | None = Field(default=None, min_length=1, max_length=128)


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    email: str | None
    phone: str | None
    role: str
    is_active: bool
    created_at: datetime


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user: UserResponse
