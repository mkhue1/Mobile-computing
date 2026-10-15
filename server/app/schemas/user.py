import re
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr, Field, ValidationInfo, field_validator
from pydantic_core import PydanticCustomError

from app.passwords import password_rejection_message

USERNAME_PATTERN = re.compile(r"^[a-zA-Z0-9_-]{3,120}$")
PASSWORD_LETTER = re.compile(r"[A-Za-z]")
PASSWORD_DIGIT = re.compile(r"\d")


def validate_username(value: str) -> str:
    if not USERNAME_PATTERN.fullmatch(value):
        raise ValueError(
            "Username must be 3–120 characters and contain only "
            "letters, numbers, underscores, or hyphens"
        )
    return value


def validate_password(value: str, user_inputs: list[str] | None = None) -> str:
    if len(value) < 8:
        raise ValueError("Password must be at least 8 characters")
    if not PASSWORD_LETTER.search(value) or not PASSWORD_DIGIT.search(value):
        raise ValueError("Password must contain at least one letter and one digit")
    message = password_rejection_message(value, user_inputs)
    if message is not None:
        raise PydanticCustomError("password_too_weak", "{message}", {"message": message})
    return value


class UserCreate(BaseModel):
    email: EmailStr
    username: str = Field(min_length=3, max_length=120)
    password: str

    @field_validator("username")
    @classmethod
    def username_format(cls, value: str) -> str:
        return validate_username(value)

    @field_validator("password")
    @classmethod
    def password_strength(cls, value: str, info: ValidationInfo) -> str:
        email = info.data.get("email")
        username = info.data.get("username")
        user_inputs = [str(item) for item in (email, username) if item]
        return validate_password(value, user_inputs)


class UserResponse(BaseModel):
    id: UUID
    email: EmailStr
    username: str
    steam_linked: bool = False
    # null means no profile picture; the image itself is served by GET /users/{id}/avatar
    avatar_updated_at: datetime | None = None

    model_config = ConfigDict(from_attributes=True)


class LoginRequest(BaseModel):
    identifier: str = Field(min_length=1)
    password: str = Field(min_length=1)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse
