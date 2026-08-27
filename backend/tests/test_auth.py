import pytest

from app.auth import hash_password, normalize_email, normalize_identifier, normalize_phone, verify_password


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


@pytest.mark.parametrize(
    ("source", "expected"),
    [("+7 912 345-67-89", "+79123456789"), ("8 (912) 345-67-89", "+79123456789")],
)
def test_phone_is_normalized(source, expected):
    assert normalize_phone(source) == expected


@pytest.mark.parametrize("value", ["123", "+abc", "+1234567890123456"])
def test_invalid_phone_is_rejected(value):
    with pytest.raises(ValueError):
        normalize_phone(value)


def test_identifier_detects_email_or_phone():
    assert normalize_identifier("User@Example.COM") == ("user@example.com", None)
    assert normalize_identifier("+79123456789") == (None, "+79123456789")
