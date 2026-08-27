from contextlib import asynccontextmanager
import hashlib
import json
from uuid import UUID

from fastapi import Depends, FastAPI, File, HTTPException, Request, UploadFile, status
from redis import Redis
from sqlalchemy import select, text
from sqlalchemy.orm import Session
from starlette.middleware.trustedhost import TrustedHostMiddleware

from app.admin import router as admin_router
from app.auth import request_user
from app.auth_routes import router as auth_router
from app.audio import MODEL_VERSION, InvalidAudioError, analyze_audio
from app.config import get_settings
from app.database import Base, engine, get_db
from app.models import Analysis, AnalysisOwnership, AuditEvent, User
from app.schemas import AnalysisResponse, HealthResponse
from app.security import enforce_rate_limit


settings = get_settings()
redis_client = Redis.from_url(settings.redis_url, decode_responses=True)


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    app.state.redis = redis_client
    yield
    redis_client.close()


app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="Backend мобильного приложения для анализа синтетической речи.",
    lifespan=lifespan,
)
app.add_middleware(
    TrustedHostMiddleware,
    allowed_hosts=[host.strip() for host in settings.allowed_hosts.split(",") if host.strip()],
)
app.include_router(admin_router)
app.include_router(auth_router)


@app.middleware("http")
async def security_headers(request: Request, call_next):
    response = await call_next(request)
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "no-referrer"
    response.headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()"
    if request.url.path.startswith("/admin"):
        response.headers["Cache-Control"] = "no-store"
    return response


@app.get("/api/v1/health", response_model=HealthResponse)
def health(db: Session = Depends(get_db)) -> HealthResponse:
    database_status = "ok"
    redis_status = "ok"
    try:
        db.execute(text("SELECT 1"))
    except Exception:
        database_status = "unavailable"
    try:
        redis_client.ping()
    except Exception:
        redis_status = "unavailable"
    overall = "ok" if database_status == redis_status == "ok" else "degraded"
    return HealthResponse(status=overall, database=database_status, redis=redis_status)


@app.post("/api/v1/analyses", response_model=AnalysisResponse, status_code=status.HTTP_201_CREATED)
async def create_analysis(
    audio: UploadFile = File(...),
    db: Session = Depends(get_db),
    _: None = Depends(enforce_rate_limit),
    user: User | None = Depends(request_user),
) -> AnalysisResponse:
    content = await audio.read(settings.max_audio_size_mb * 1024 * 1024 + 1)
    if len(content) > settings.max_audio_size_mb * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Аудиофайл превышает допустимый размер")
    if not content:
        raise HTTPException(status_code=400, detail="Получен пустой файл")

    content_hash = hashlib.sha256(content).hexdigest()
    cache_key = f"analysis:{MODEL_VERSION}:{content_hash}"
    cached = redis_client.get(cache_key)
    if cached:
        payload = json.loads(cached)
        payload["cached"] = True
        record_id = payload.get("id")
        if user is not None and record_id:
            link_analysis_to_user(db, user.id, UUID(record_id))
        db.add(
            AuditEvent(
                action="analysis.completed",
                outcome="cache",
                resource_id=str(payload.get("id", "")) or None,
            )
        )
        db.commit()
        return AnalysisResponse.model_validate(payload)

    existing = db.scalar(select(Analysis).where(Analysis.content_hash == content_hash))
    if existing and existing.model_version == MODEL_VERSION:
        if user is not None:
            link_analysis_to_user(db, user.id, existing.id)
            db.commit()
        response = AnalysisResponse.model_validate(existing)
        redis_client.setex(cache_key, settings.cache_ttl_seconds, response.model_dump_json())
        return response.model_copy(update={"cached": True})

    try:
        prediction = analyze_audio(content, audio.filename or "audio.wav")
    except InvalidAudioError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc

    if existing:
        existing.file_name = audio.filename or "audio.wav"
        existing.verdict = prediction.verdict
        existing.synthetic_probability = prediction.probability
        existing.model_version = prediction.model_version
        record = existing
    else:
        record = Analysis(
            file_name=audio.filename or "audio.wav",
            content_hash=content_hash,
            verdict=prediction.verdict,
            synthetic_probability=prediction.probability,
            model_version=prediction.model_version,
        )
        db.add(record)
    db.commit()
    db.refresh(record)
    if user is not None:
        link_analysis_to_user(db, user.id, record.id)
    db.add(
        AuditEvent(
            action="analysis.completed",
            outcome="success",
            resource_id=str(record.id),
        )
    )
    db.commit()
    response = AnalysisResponse.model_validate(record)
    redis_client.setex(cache_key, settings.cache_ttl_seconds, response.model_dump_json())
    return response


@app.get("/api/v1/analyses", response_model=list[AnalysisResponse])
def list_analyses(
    db: Session = Depends(get_db),
    user: User | None = Depends(request_user),
) -> list[Analysis]:
    query = select(Analysis)
    if user is not None:
        query = query.join(AnalysisOwnership).where(AnalysisOwnership.user_id == user.id)
    return list(db.scalars(query.order_by(Analysis.created_at.desc()).limit(100)))


@app.get("/api/v1/analyses/{analysis_id}", response_model=AnalysisResponse)
def get_analysis(
    analysis_id: UUID,
    db: Session = Depends(get_db),
    user: User | None = Depends(request_user),
) -> Analysis:
    record = db.get(Analysis, analysis_id)
    if record is None:
        raise HTTPException(status_code=404, detail="Результат анализа не найден")
    if user is not None:
        ownership = db.scalar(
            select(AnalysisOwnership).where(
                AnalysisOwnership.user_id == user.id,
                AnalysisOwnership.analysis_id == analysis_id,
            )
        )
        if ownership is None:
            raise HTTPException(status_code=404, detail="Analysis result not found")
    return record


def link_analysis_to_user(db: Session, user_id: UUID, analysis_id: UUID) -> None:
    existing = db.scalar(
        select(AnalysisOwnership).where(
            AnalysisOwnership.user_id == user_id,
            AnalysisOwnership.analysis_id == analysis_id,
        )
    )
    if existing is None:
        db.add(AnalysisOwnership(user_id=user_id, analysis_id=analysis_id))
