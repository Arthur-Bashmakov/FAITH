import pytest

from app.auth import hash_password, normalize_email, verify_password


def test_password_hash_is_salted_and_verifiable():
    first = hash_password("correct horse battery staple")
    second = hash_password("correct horse battery staple")

    assert first != second
    assert "correct horse" not in first
    assert verify_password("correct horse battery staple", first)
    assert not verify_password("wrong password", first)


def test_email_is_normalized():
    assert normalize_email("  User@Example.COM ") == "user@example.com"


@pytest.mark.parametrize("value", ["missing-at", "@example.com", "user@localhost"])
def test_invalid_email_is_rejected(value):
    with pytest.raises(ValueError):
        normalize_email(value)
