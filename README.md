# FAITH

[Русский](#русский) · [English](#english)

## Русский

FAITH — клиент-серверное Android-приложение для исследовательской оценки происхождения речи в аудиозаписи. Пользователь выбирает файл или записывает голос, приложение отправляет аудио на защищённый REST API, а модель возвращает оценку сходства с синтетической речью.

> Результат является оценкой исследовательского классификатора, а не экспертным заключением. Пограничные случаи отображаются как неопределённые.

### Возможности

- выбор WAV, MP3, M4A, AAC, OGG и FLAC;
- запись голоса с таймером и живой амплитудной дорожкой;
- приём аудио через системное меню Android «Поделиться»;
- предварительное прослушивание, пауза и индикатор прогресса;
- отображение имени, формата, размера, длительности и доступных метаданных;
- последовательные экраны выбора, предпросмотра, обработки и результата;
- история анализов для авторизованного пользователя;
- русский и английский интерфейс;
- регистрация и вход по email; гостевой режим остаётся доступным;
- отмена сетевого запроса и понятные сообщения об ошибках.

### Архитектура и стек

- **Android:** Kotlin, Jetpack Compose, OkHttp, Android Keystore;
- **API:** Python 3.11, FastAPI, SQLAlchemy;
- **модель:** AASIST-L, обученная на ASVspoof 2019 LA;
- **данные:** PostgreSQL;
- **кэш:** Redis, ключ формируется из SHA-256 аудио и версии анализатора;
- **развёртывание:** Docker Compose на VPS;
- **HTTPS:** Caddy автоматически получает и продлевает TLS-сертификат для `faith-audio.ru`.

```text
Android → HTTPS/Caddy → FastAPI → AASIST-L
                              ├─ PostgreSQL
                              └─ Redis
```

### Обработка аудио

Входная запись преобразуется в mono PCM 16 кГц. Тишина и простые тональные сигналы отклоняются до запуска модели. Для тихой речи используется ограниченное адаптивное усиление. Длинные записи анализируются по нескольким фрагментам, а результат агрегируется устойчивым способом.

Текущие консервативные зоны интерфейса:

- менее 40% — вероятно человеческая речь;
- 40–95% — результат неопределён;
- от 95% — вероятно синтетическая речь.

Пороги требуют окончательной проверки на отдельном размеченном наборе русской речи. Методика описана в `docs/model-evaluation.md`.

### Локальный запуск backend

1. Скопировать `.env.example` в `.env` и заменить демонстрационные пароли.
2. Запустить Docker Desktop.
3. В каталоге проекта выполнить:

```powershell
docker compose up --build -d
docker compose ps
```

Проверка: <http://localhost:8000/api/v1/health>  
Документация API: <http://localhost:8000/docs>

Остановка без удаления данных:

```powershell
docker compose stop
```

### Запуск Android

1. Открыть каталог `android/` в Android Studio.
2. Выбрать Pixel 4 API 29 или физическое Android-устройство.
3. Запустить конфигурацию `app`.

Production-адрес уже задан приложением: `https://faith-audio.ru/`. Пользователю не требуется вводить IP, выбирать сервер или запускать локальный туннель.

Сборка из PowerShell:

```powershell
cd android
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug test
```

### Развёртывание на VPS

Production использует `compose.yaml` вместе с `compose.prod.yaml`. Исходный код переносится на VPS в `/opt/faith`, после чего образ API собирается непосредственно на сервере. Локальные Docker-образы автоматически на VPS не передаются.

Подробная инструкция: `docs/VPS_DEPLOYMENT.md`.

### Безопасность

- секреты хранятся только в локальном `.env`, который исключён из Git;
- шаблоны `.env.example` и `.env.production.example` не содержат рабочих паролей;
- PostgreSQL и Redis не публикуются в интернет;
- API доступен через HTTPS;
- токен Android хранится через Android Keystore;
- загрузка ограничена по MIME-типу и размеру;
- административный маршрут защищён отдельными учётными данными;
- `scripts/check_repository.ps1` проверяет проект на локальные пути, приватные ключи и случайно записанные токены.

Проверка перед публикацией:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check_repository.ps1
```

OAuth/OIDC для внешних провайдеров требует регистрации приложения у каждого провайдера и отдельных секретов. Эти секреты нельзя добавлять в репозиторий.

---

## English

FAITH is a client-server Android application for research-oriented assessment of speech origin in audio recordings. A user selects a file or records speech, the application uploads it to a secured REST API, and the model returns a synthetic-speech similarity score.

> The result is a research classifier score, not an expert conclusion. Borderline cases are reported as uncertain.

### Features

- WAV, MP3, M4A, AAC, OGG and FLAC input;
- voice recording with a timer and live amplitude waveform;
- Android share-sheet integration;
- play/pause preview with progress indication;
- file name, format, size, duration and available metadata;
- separate selection, preview, processing and result screens;
- analysis history for authenticated users;
- Russian and English UI;
- email registration and sign-in with an optional guest mode;
- request cancellation and user-friendly error states.

### Architecture and technology

- **Android:** Kotlin, Jetpack Compose, OkHttp, Android Keystore;
- **API:** Python 3.11, FastAPI, SQLAlchemy;
- **model:** AASIST-L trained on ASVspoof 2019 LA;
- **storage:** PostgreSQL;
- **cache:** Redis keyed by audio SHA-256 and analyzer version;
- **deployment:** Docker Compose on a VPS;
- **HTTPS:** Caddy automatically obtains and renews the TLS certificate for `faith-audio.ru`.

### Local backend

Copy `.env.example` to `.env`, replace demonstration passwords, start Docker Desktop, and run:

```powershell
docker compose up --build -d
docker compose ps
```

Health check: <http://localhost:8000/api/v1/health>  
API documentation: <http://localhost:8000/docs>

### Android client

Open `android/` in Android Studio and run the `app` configuration on a Pixel 4 API 29 emulator or a physical Android device. The production endpoint is embedded in the application as `https://faith-audio.ru/`; end users do not select a server or enter an IP address.

### Security and release checks

Runtime secrets belong only in an untracked `.env` file. Databases are not exposed publicly, production traffic uses HTTPS, Android tokens are protected by Android Keystore, and uploads are restricted by content type and size.

Before publishing, run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check_repository.ps1
```

Detailed deployment, security and evaluation notes are available in `docs/`.

## Project status

The repository contains a working diploma-project prototype. The mobile client, API, database, cache, HTTPS deployment and core inference flow are implemented. Final model thresholds still require validation on an independent labeled Russian speech dataset before the system can be treated as a production-grade detector.
