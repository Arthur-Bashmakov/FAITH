FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.3.1 \
    && pip install --no-cache-dir -r requirements.txt

# Official AASIST implementation and pretrained AASIST-L weights (MIT).
# The exact commit is pinned so that builds remain reproducible.
ARG AASIST_COMMIT=a04c9863f63d44471dde8a6abcb3b082b07cd1d1
RUN git clone --filter=blob:none https://github.com/clovaai/aasist.git /opt/aasist \
    && git -C /opt/aasist checkout "$AASIST_COMMIT" \
    && test "$(sha256sum /opt/aasist/models/weights/AASIST-L.pth | cut -d ' ' -f 1)" = "814331d088032bb4c3fa61cc014789eadeed464209dd094ab3a2dd6ffbdce27a"
COPY app ./app
COPY scripts ./scripts

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
