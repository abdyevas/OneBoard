# OneBoard

Агрегатор вакансий с адаптацией CV под конкретную вакансию.

- **Backend:** Java 21, Spring Boot 4, Spring Data JPA, H2 (файловая БД в `backend/data/`)
- **Frontend:** обычный HTML/JS без сборки, отдаётся самим Spring (`backend/src/main/resources/static`)
- **Адаптация CV:** локальный алгоритм по ключевым навыкам (работает без ключей) и Claude (`claude-opus-5-5`, если задан `ANTHROPIC_API_KEY`)

## Запуск

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Откройте http://localhost:8085. Другой порт: `$env:PORT=9000; .\mvnw.cmd spring-boot:run`.

Включить адаптацию через Claude:

```powershell
$env:ANTHROPIC_API_KEY="sk-ant-..."; .\mvnw.cmd spring-boot:run
```

Подключить азербайджанские вакансии через Jooble (ключ бесплатный, запрашивается на https://az.jooble.org/api/about):

```powershell
$env:JOOBLE_API_KEY="..."; .\mvnw.cmd spring-boot:run
```

Тесты: `.\mvnw.cmd test`

## Азербайджанские площадки

OneBoard собирает только азербайджанские вакансии. Условия сайтов проверены в ноябре 2026:

| Площадка | Условия | Как подключено |
|---|---|---|
| jobsearch.az | условий использования нет; robots.txt API-хоста: `Crawl-delay: 30` | загружается напрямую: список по запросу, описания в фоне — не чаще 1 запроса в 30 с |
| smartjob.az | условия не ограничивают; robots.txt закрывает только `/api/` | загружается напрямую: sitemap + данные `JobPosting` со страниц, 1 запрос в 2 с, только новые/обновлённые |
| Jooble (az.jooble.org) | официальный партнёрский API | источник `jooble`, нужен `JOOBLE_API_KEY` |
| hellojob.az, busy.az, boss.az, Glorri | прямо запрещают автоматический сбор данных | не загружаются; только кнопки, открывающие поиск на сайте |

Каждая вакансия ведёт на оригинал. Если сайт изменит правила — источник нужно отключить.
Для hellojob.az, busy.az, boss.az и Glorri загрузка возможна только с их письменного разрешения или через официальный API.

## Как устроено

```
vacancy/            Vacancy (сущность), поиск, ручное добавление
vacancy/source/     VacancySource — источники (jobsearch.az, smartjob.az, Jooble); PoliteHttp — паузы между запросами к сайту
aggregation/        фоновая загрузка из всех источников + upsert без дублей (source + externalId), опционально по cron;
                    DetailEnricher — фоновая подгрузка полных описаний (открытые вакансии — первыми)
cv/                 базовые CV: текст/Markdown, загрузка .pdf/.txt/.md
adaptation/         SkillExtractor (словарь навыков, скоринг), CvAdapter: keyword | claude, история адаптаций
```

Новый источник вакансий — это один класс, реализующий `VacancySource` и помеченный `@Component`.

## API

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/api/vacancies?q=&source=&remote=&page=&size=` | поиск вакансий |
| GET | `/api/vacancies/{id}` | вакансия целиком |
| POST | `/api/vacancies` | добавить вакансию вручную |
| GET | `/api/vacancies/stats` | количество по источникам |
| GET | `/api/aggregation/sources` | список источников |
| POST | `/api/aggregation/refresh` `{query, sources[]}` | запустить загрузку свежих вакансий (в фоне) |
| GET | `/api/aggregation/status` | прогресс загрузки |
| GET/PUT/DELETE | `/api/applications`, `/api/applications/{vacancyId}` | статус отклика на вакансию |
| GET/POST/PUT/DELETE | `/api/cvs`, `/api/cvs/{id}` | CRUD для CV |
| POST | `/api/cvs/upload` (multipart `file`, `title`) | CV из файла |
| GET | `/api/cvs/{id}/matches?q=&limit=` | вакансии, лучше всего подходящие к CV |
| GET | `/api/match?cvId=&vacancyId=` | совпадение навыков без генерации |
| POST | `/api/adaptations` `{cvId, vacancyId, method: auto/keyword/claude}` | адаптировать CV |
| GET | `/api/adaptations?cvId=&vacancyId=` | история |
| GET | `/api/adaptations/methods` | какие методы доступны |

## Настройки (`application.properties`)

- `oneboard.aggregation.schedule-enabled` / `cron` / `default-queries` — автообновление вакансий
- `oneboard.claude.model`, `oneboard.claude.max-tokens` — параметры Claude

Claude получает строгую инструкцию не выдумывать опыт и навыки: только переформулировать, переставлять и акцентировать то, что уже есть в CV.
