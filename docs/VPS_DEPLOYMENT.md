# Развёртывание FAITH на VPS

Production-запуск использует `compose.yaml` вместе с `compose.prod.yaml`.
PostgreSQL, Redis и FastAPI не публикуют свои порты в интернет. Входящий
трафик принимает Caddy на портах 80/443 и передаёт его контейнеру API.

## Первый запуск по IP

1. Скопировать проект в `/opt/faith` без `.env`, сборочных каталогов и IDE-кэшей.
2. Скопировать `.env.production.example` в `.env` и заменить все `change_me`.
3. Запустить:

   ```bash
   docker compose -f compose.yaml -f compose.prod.yaml up --build -d
   ```

4. Проверить:

   ```bash
   docker compose -f compose.yaml -f compose.prod.yaml ps
   curl http://127.0.0.1/api/v1/health
   ```

До подключения домена используется HTTP только для первичной серверной проверки.
Итоговая Android release-сборка должна обращаться к домену по HTTPS.

## Обновление

После загрузки новой версии исходников:

```bash
docker compose -f compose.yaml -f compose.prod.yaml up --build -d
docker image prune -f
```

Команда `docker compose down -v` запрещена для обычного обновления: параметр `-v`
удалит том PostgreSQL вместе с данными.
