import re
from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

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


def validate_password(value: str) -> str:
    if len(value) < 8:
        raise ValueError("Password must be at least 8 characters")
    if not PASSWORD_LETTER.search(value) or not PASSWORD_DIGIT.search(value):
        raise ValueError("Password must contain at least one letter and one digit")
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
    def password_strength(cls, value: str) -> str:
        return validate_password(value)


class UserResponse(BaseModel):
    id: UUID
    email: EmailStr
    username: str

    model_config = ConfigDict(from_attributes=True)


class LoginRequest(BaseModel):
    identifier: str = Field(min_length=1)
    password: str = Field(min_length=1)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse
