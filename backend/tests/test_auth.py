import pytest
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker

from app.auth import hash_password, normalize_email, normalize_identifier, normalize_phone, verify_password
from app import auth_routes
from app.database import Base
from app.models import Analysis, AnalysisOwnership, AuditEvent, User
from app.schemas import DeleteAccountRequest


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


def test_delete_account_removes_orphaned_analysis_but_preserves_shared_result(monkeypatch):
    engine = create_engine("sqlite+pysqlite:///:memory:")
    Base.metadata.create_all(engine)
    session = sessionmaker(bind=engine, expire_on_commit=False)()
    password = "correct horse battery staple"
    owner = User(email="owner@example.com", password_hash=hash_password(password))
    other = User(email="other@example.com", password_hash=hash_password(password))
    orphaned = Analysis(
        file_name="private.wav",
        content_hash="a" * 64,
        verdict="human",
        synthetic_probability=0.1,
        model_version="test",
    )
    shared = Analysis(
        file_name="shared.wav",
        content_hash="b" * 64,
        verdict="uncertain",
        synthetic_probability=0.5,
        model_version="test",
    )
    session.add_all([owner, other, orphaned, shared])
    session.flush()
    session.add_all(
        [
            AnalysisOwnership(user_id=owner.id, analysis_id=orphaned.id),
            AnalysisOwnership(user_id=owner.id, analysis_id=shared.id),
            AnalysisOwnership(user_id=other.id, analysis_id=shared.id),
            AuditEvent(action="auth.login", outcome="success", resource_id=str(owner.id)),
            AuditEvent(action="analysis.completed", outcome="success", resource_id=str(orphaned.id)),
        ]
    )
    session.commit()
    owner_id = owner.id
    orphaned_id = orphaned.id
    shared_id = shared.id
    monkeypatch.setattr(auth_routes, "enforce_rate_limit", lambda request: None)

    response = auth_routes.delete_account(
        DeleteAccountRequest(password=password),
        request=object(),
        user=owner,
        db=session,
    )

    assert response.status_code == 204
    assert session.get(User, owner_id) is None
    assert session.get(Analysis, orphaned_id) is None
    assert session.get(Analysis, shared_id) is not None
    assert session.scalar(
        select(AnalysisOwnership.id).where(AnalysisOwnership.user_id == owner_id)
    ) is None
    assert session.scalar(
        select(AuditEvent.id).where(AuditEvent.resource_id.in_([str(owner_id), str(orphaned_id)]))
    ) is None
