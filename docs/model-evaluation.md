# Проверка и калибровка модели FAITH

Текущая версия использует предобученную AASIST-L. Число, возвращаемое моделью,
является оценкой классификатора, а не гарантированной вероятностью. Перед практическим использованием
пороги нужно проверить на собственном наборе русской речи.

## Структура локального набора

Личные записи не должны попадать в Git. Создайте каталоги:

```text
evaluation-data/
  human/
    phone/
    messenger/
  synthetic/
    generator-a/
    generator-b/
```

Рекомендуемый минимум для первичной проверки — по 30 файлов каждого класса от
нескольких дикторов и нескольких генераторов. Один и тот же исходный голос нельзя
одновременно использовать для подбора порогов и финальной проверки.

## Запуск внутри контейнера API

```powershell
docker compose up -d
docker compose cp evaluation-data api:/tmp/faith-evaluation
docker compose exec api python scripts/evaluate_model.py /tmp/faith-evaluation
docker compose cp api:/app/evaluation-report.json .\evaluation-report.json
docker compose cp api:/app/evaluation-scores.csv .\evaluation-scores.csv
```

Отчёт содержит матрицу ошибок, accuracy только для уверенных ответов,
precision/recall/F1 для синтетической речи и долю неопределённых результатов.
Также выводятся предлагаемые пороги. Их нельзя переносить в приложение, пока они
не проверены на отдельной контрольной части данных.

