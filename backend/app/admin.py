from __future__ import annotations

from html import escape

from fastapi import APIRouter, Depends
from fastapi.responses import HTMLResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.audio import MODEL_VERSION
from app.database import get_db
from app.models import Analysis, AuditEvent, User
from app.security import require_admin


router = APIRouter(prefix="/admin", tags=["admin"])


@router.get("", response_class=HTMLResponse)
def admin_panel(
    _: str = Depends(require_admin),
    db: Session = Depends(get_db),
) -> HTMLResponse:
    total = db.scalar(select(func.count()).select_from(Analysis)) or 0
    users = db.scalar(select(func.count()).select_from(User)) or 0
    verdict_rows = db.execute(
        select(Analysis.verdict, func.count()).group_by(Analysis.verdict)
    ).all()
    recent = list(db.scalars(select(Analysis).order_by(Analysis.created_at.desc()).limit(20)))
    audit = list(db.scalars(select(AuditEvent).order_by(AuditEvent.created_at.desc()).limit(20)))
    verdicts = {name: count for name, count in verdict_rows}
    rows = "".join(
        "<tr>"
        f"<td>{escape(item.file_name)}</td>"
        f"<td>{escape(item.verdict)}</td>"
        f"<td>{item.synthetic_probability:.1%}</td>"
        f"<td>{escape(item.created_at.isoformat() if item.created_at else '')}</td>"
        "</tr>"
        for item in recent
    )
    audit_rows = "".join(
        "<tr>"
        f"<td>{escape(item.action)}</td>"
        f"<td>{escape(item.outcome)}</td>"
        f"<td>{escape(item.resource_id or '')}</td>"
        f"<td>{escape(item.created_at.isoformat() if item.created_at else '')}</td>"
        "</tr>"
        for item in audit
    )
    html = f"""<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>FAITH Admin</title><style>
body{{font-family:system-ui;background:#0d091b;color:#f4efff;margin:0;padding:32px}}
h1{{font-weight:400}}.cards{{display:flex;gap:16px;flex-wrap:wrap}}.card{{background:#211733;padding:18px;border-radius:14px;min-width:170px}}
.value{{font-size:28px;color:#bd86ff}}table{{width:100%;border-collapse:collapse;margin-top:24px;background:#171024}}
th,td{{padding:11px;text-align:left;border-bottom:1px solid #38284d}}th{{color:#d8b8ff}}small{{color:#aa9dbb}}
</style></head><body><h1>FAITH — панель администратора</h1><small>Модель: {escape(MODEL_VERSION)}</small>
<div class="cards"><div class="card">Всего анализов<div class="value">{total}</div></div>
<div class="card">Пользователи<div class="value">{users}</div></div>
<div class="card">Человеческая речь<div class="value">{verdicts.get('human', 0)}</div></div>
<div class="card">Синтетическая речь<div class="value">{verdicts.get('synthetic', 0)}</div></div>
<div class="card">Неопределённо<div class="value">{verdicts.get('uncertain', 0)}</div></div></div>
<h2>Последние проверки</h2><table><thead><tr><th>Файл</th><th>Вердикт</th><th>Оценка</th><th>Дата</th></tr></thead><tbody>{rows}</tbody></table>
<h2>Аудит безопасности</h2><table><thead><tr><th>Событие</th><th>Результат</th><th>Ресурс</th><th>Дата</th></tr></thead><tbody>{audit_rows}</tbody></table>
</body></html>"""
    db.add(AuditEvent(action="admin.panel.view", outcome="success"))
    db.commit()
    return HTMLResponse(html, headers={"Cache-Control": "no-store"})
