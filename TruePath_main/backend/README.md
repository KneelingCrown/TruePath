# True Path — backend

Серверная часть True Path: загрузка материалов, оркестрация фактчекинга, граф доказательств,
история, поток событий для Android-клиента. План реализации — `Hack_rep/TRUEPATH_BACKEND_PLAN.md`.

**Текущий этап — без нейросетей и внешних API (по ТЗ):** роли LLM/поиска/OCR выполняют
детерминированные заглушки за интерфейсами `LLMClient`/`SearchClient`. Итоговая проверка —
описание кода, API-ключи не нужны.

Для Android-клиента backend играет роль «поставщика исследования» из `docs/API_CONTRACT.md`:
клиент запускает задачу, получает промежуточные события и восстанавливает её по ID.

## Запуск

```bash
python3 -m venv .venv                # из корня TruePath
.venv/bin/pip install fastapi "uvicorn[standard]" "sqlalchemy>=2.0" alembic pydantic-settings httpx pypdf python-multipart
cd backend
cp .env.example .env                 # при необходимости поправить DATABASE_URL / UPLOADS_DIR
../.venv/bin/alembic upgrade head    # создать таблицы
../.venv/bin/uvicorn app.main:app --reload
```

Swagger UI: http://127.0.0.1:8000/docs

Android-эмулятор обращается к серверу хоста по `http://10.0.2.2:8000` (см. `BackendResearchSource`).

## Демо-сценарий для итоговой проверки

Воспроизводится локально, без ключей и нейросетей:

```bash
# 1. Материал с утверждением, которое первоисточник ограничивает
MID=$(curl -s -X POST http://127.0.0.1:8000/api/v1/materials -H "Content-Type: application/json" \
  -d '{"kind":"text","content":"Оргкомитет сообщил: начало фестиваля перенесено на воскресенье 25 октября."}' \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

# 2. Запуск проверки (идемпотентно: повтор с тем же client_task_id вернёт ту же задачу)
RID=$(curl -s -X POST http://127.0.0.1:8000/api/v1/research -H "Content-Type: application/json" \
  -d "{\"material_id\":$MID,\"client_task_id\":\"demo-1\"}" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['id'])")

# 3. Живой граф: события с курсором (stage → upsert_node → upsert_edge → finished)
curl -s "http://127.0.0.1:8000/api/v1/research/$RID/events?after=0"

# 4. Итог: утверждение insufficient («переносится только одно направление» — не опровержение)
curl -s "http://127.0.0.1:8000/api/v1/research/$RID/result"

# 5. Задача + «План Б»
curl -s -X POST http://127.0.0.1:8000/api/v1/plans -H "Content-Type: application/json" \
  -d "{\"claim_id\":1,\"text\":\"Купить билеты заранее\"}"
curl -s "http://127.0.0.1:8000/api/v1/plans"

# 6. Лента: кластер из трёх публикаций об одном событии
curl -s "http://127.0.0.1:8000/api/v1/feed"

# 7. Обновление исследования: прежний результат сохраняется, статусы могут измениться
curl -s -X POST "http://127.0.0.1:8000/api/v1/research/$RID/refresh"
```

## Миграции

```bash
cd backend
../.venv/bin/alembic upgrade head          # накатить
../.venv/bin/alembic revision --autogenerate -m "..."  # после изменения app/models.py
```

URL БД берётся из `DATABASE_URL` (`.env` или переменная окружения), по умолчанию SQLite.

## Тесты

```bash
cd backend
../.venv/bin/pytest -q
```

## Структура

```
backend/
├── app/
│   ├── main.py        # FastAPI-приложение, роутеры с префиксом /api/v1
│   ├── config.py      # pydantic-settings: DATABASE_URL, UPLOADS_DIR, EXTRA_SEARCH_MAX_QUERIES
│   ├── db.py          # engine, sessionmaker, Base
│   ├── models.py      # 13 таблиц: лента, материалы, исследования, граф, планы
│   ├── schemas.py     # pydantic-схемы запросов/ответов
│   ├── adapters/      # заглушки внешних способностей (без API-ключей)
│   │   ├── base.py         # контракты LLMClient / SearchClient / EvidenceMatch
│   │   ├── rule_llm.py     # RuleBasedLLM: этапы 1, 2 и 4 пайплайна по правилам
│   │   ├── local_search.py # LocalSearch: поиск по фикстурной базе
│   │   └── fixtures.py     # демо-источники (сюжет из концепции)
│   ├── services/
│   │   ├── extract.py  # скачивание ссылки, HTML→текст, PDF→текст (pypdf)
│   │   ├── storage.py  # сохранение загруженных файлов
│   │   ├── pipeline.py # оркестратор, этапы 1–6
│   │   └── events.py   # поток событий исследования для клиента (курсор + стабильные id)
│   └── routers/
│       ├── health.py
│       ├── materials.py  # POST /materials (текст/ссылка), POST /materials/file
│       ├── research.py   # запуск, статус, результат, граф, события, обновление
│       └── history.py    # история и поиск
├── migrations/        # Alembic
└── tests/
```

## API

Материалы:
- `POST /api/v1/materials` — JSON: `{"kind": "text", "content": "..."}` или `{"kind": "link", "url": "..."}`.
- `POST /api/v1/materials/file` — multipart: PDF (извлечение текста), изображение (предупреждение «OCR не подключён»), txt.
- Ответ: `{id, kind, has_extracted_text, warning}`. Недоступная ссылка → 502, файл >10 МБ → 413.

Исследования:
- `POST /api/v1/research` — `{material_id, context?, client_task_id?}`. Повторный вызов с тем же
  `client_task_id` возвращает существующую задачу (идемпотентность, 200 вместо 201).
- `GET /api/v1/research/{id}` — статус, этапы, утверждения с вопросами и доказательствами.
- `GET /api/v1/research/{id}/result` — краткий вывод + утверждения со статусами и объяснениями.
- `GET /api/v1/research/{id}/graph` — узлы и связи с объяснениями (`is_established` для предполагаемых).
- `GET /api/v1/research/{id}/events?after=N` — поток событий для клиента: `stage` / `upsert_node` /
  `upsert_edge` / `finished` / `failed` с курсором `seq` и стабильными id (повторная доставка безопасна).
- `POST /api/v1/research/{id}/refresh` — новая проверка того же материала; прежний результат сохраняется.

История:
- `GET /api/v1/history?q=` — список исследований с поиском по материалу/контексту/итогу.
- `GET /api/v1/history/{id}` — сохранённое исследование без повторного запуска.

Задачи и «План Б»:
- `POST /api/v1/plans` — `{claim_id, text}`: связать задачу пользователя с утверждением.
- `GET /api/v1/plans?research_id=` — задачи с утверждением, карточкой «План Б» и подсветкой
  `changed`/`current_status`, если при обновлении исследования статус утверждения изменился.
  Никаких автоматических переносов задач — только предложение.

Лента:
- `GET /api/v1/feed?q=&topic=&exclude=` — события (кластеры публикаций) с шаблонным саммари,
  темой, числом источников и отметкой `checked` («событие уже проверялось» → research_id).
- `POST /api/v1/research` принимает `article_id` вместо `material_id` — проверка публикации
  из ленты (материал создаётся из её текста).

Пайплайн (этапы 1–6): разбор материала → вопросы → поиск оснований (LocalSearch) →
сопоставление цитат (RuleBasedLLM) → дополнительный поиск с бюджетом (`EXTRA_SEARCH_MAX_QUERIES`) →
статусы утверждений + сборка графа. Отсутствие подтверждения — `insufficient`, не `refuted`;
ошибка этапа — `failed` с человекочитаемой причиной, никогда не псевдорезультат.

## Статус

- [x] Шаг 0 — каркас, health, конфиг, тесты
- [x] Шаг 1 — модель данных (13 таблиц), миграция, тесты roundtrip
- [x] Шаг 2 — загрузка материалов: текст, ссылка, файл (PDF/txt/изображение-заглушка)
- [x] Шаг 3 — адаптеры-заглушки `RuleBasedLLM` / `LocalSearch` (без ключей)
- [x] Шаг 4 — оркестратор, этапы 1–2, эндпоинты запуска и статуса
- [x] Шаг 5 — этапы 3–6: поиск, сопоставление, статусы, граф
- [x] Шаг 6 — результат, история, граф, обновление; поток событий для клиента
- [x] Шаг 7 — OCR/медиа: остаётся явной заглушкой по ТЗ (загрузка не падает, возвращает предупреждение)
- [x] Шаг 8 — лента: фикстурные публикации, кластеризация без эмбеддингов, саммари, темы, отметка «проверялось»; запуск проверки по `article_id`
- [x] Шаг 9 — «План Б»: карточки в этапе 6, `POST/GET /plans`, подсветка изменившихся утверждений, `plan_b` в событии `finished`
- [x] Шаг 10 — застрявшие queued/running → `failed` при следующем запуске, демо-сценарий в README, обработка ошибок
