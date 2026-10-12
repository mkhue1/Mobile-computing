from zxcvbn import zxcvbn

# 3 is zxcvbn's "safely unguessable" score. 0 and 1 are common passwords
# and dictionary words, including a digit or lookalike letter stuck on the end.
MIN_PASSWORD_SCORE = 3


def password_rejection_message(password: str, user_inputs: list[str] | None = None) -> str | None:
    """None when zxcvbn considers the password hard to guess."""
    result = zxcvbn(password, user_inputs=[item for item in (user_inputs or []) if item])
    if result["score"] >= MIN_PASSWORD_SCORE:
        return None

    feedback = result["feedback"]
    print(feedback)
    warning = (feedback.get("warning") or "").strip()
    suggestions = feedback.get("suggestions") or []
    suggestion = suggestions[0].strip() if suggestions else ""
    if warning and suggestion:
        return f"{warning} {suggestion}"
    return warning or suggestion or "Password is too easy to guess."
