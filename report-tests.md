# Звіт про інтеграційне та end-to-end тестування Food Rescue

**Дата актуального прогону:** 28.10.2026  
**Проєкт:** Food Rescue (Spring Boot 4.1.1, Java 25, Spring Modulith)  
**Команда повної перевірки (Windows / PowerShell):** `.\mvnw.cmd clean verify`  
**Середовище:** Docker Desktop, PostgreSQL 17 у Testcontainers, Chromium для Playwright.  
**Результат:** **BUILD SUCCESS**; 421 тест, 0 failures, 0 errors, 0 skipped; **01:23 min**.

Підсумкові кількості та загальний час підтверджено останнім локальним запуском `clean verify` (28.10.2026). Розподіл за рівнями оновлено після додавання 8 тестів у `VolunteerCrudIntegrationTest`. Час окремих класів та суми за рівнями потребують даних нового запуску з `target/surefire-reports/TEST-*.xml` і `target/failsafe-reports/TEST-*.xml`; попередні значення не перенесено, щоб не змішувати вимірювання різних прогонів.

## 1. Рівні й обсяг тестування

| Рівень | Засоби та призначення | Maven-фаза | Класів | Тестів |
|---|---|---|---:|---:|
| Швидкі | `*Test`, окрім інтеграційних; модульні, MVC/JPA-зрізи, security, Modulith | `test` | 37 | 305 |
| Інтеграційні | `@IntegrationTest`: MockMvc + реальна PostgreSQL + JdbcClient | `test` | 9 | 103 |
| REST E2E | `@E2ETest`: RestTestClient + запущений HTTP-сервер | `verify` | 3 | 8 |
| Браузерні E2E | `@E2ETest`: Playwright + Page Object | `verify` | 3 | 5 |
| **Разом** | | `verify` | **52** | **421** |

**Surefire:** 46 класів і **408 тестів** (305 швидких + 103 інтеграційні).  
**Failsafe:** 6 класів і **13 тестів** (8 REST E2E + 5 браузерних E2E).

Загальний час `clean verify` з урахуванням компіляції, запуску контейнерів та інших фаз Maven становив **01:23 min**.

### Розподіл інтеграційних і E2E-тестів за модулями

| Модуль | Інтеграційні тести MockMvc | REST E2E | Playwright |
|---|---|---|---|
| `lot` | `LotCrudIntegrationTest` (28), `LotFormIntegrationTest` (6) | `LotApiE2EIT` (4) | `LotUiE2EIT` (3) |
| `volunteer` | `VolunteerCrudIntegrationTest` (15), `ReservationIntegrationTest` (16) | `ReservationApiE2EIT` (2) | `ReservationUiE2EIT` (1) |
| `delivery` | `DestinationPointCrudIntegrationTest` (12), `DeliveryFlowIntegrationTest` (17), `DeliveryAlertIntegrationTest` (3) | `DeliveryFlowE2EIT` (2) | `DestinationPointUiE2EIT` (1) |
| Спільні компоненти | `CurrentUserIntegrationTest` (3), `TraceIdIntegrationTest` (3) | — | — |

Сукупно: **9 інтеграційних класів / 103 тести**, **3 REST E2E-класи / 8 тестів**, **3 браузерні класи / 5 тестів**.

## 2. Що саме перевірено

- **CRUD і робота з даними:** лоти, волонтери, пункти призначення; успішні та негативні HTTP-відповіді, валідація й перевірка записів PostgreSQL через `JdbcClient`.
- **Бізнес-процеси:** публікація та резервування лота, передача продуктів, доставка, підтвердження (`CONFIRMED`) і спірний результат (`DISPUTED`), обмеження на переходи статусів.
- **Безпека:** доступ користувачів із ролями `DONOR`, `VOLUNTEER`, `COORDINATOR`, помилки авторизації, а також перевірка `X-Trace-Id`.
- **Зовнішній HTTP-сервіс:** `DeliveryAlertNotifier` відправляє сповіщення; WireMock перевіряє коректний JSON, відповідь `500` і тайм-аут. Збій сповіщення не скасовує завершену доставку.
- **REST E2E:** реальні HTTP-запити через `RestTestClient` до застосунку на випадковому порту.
- **Браузерні E2E:** дії користувачів у Chromium через Playwright із Page Object: логін, створення лота, перевірки доступу, резервування, створення пункту призначення.

## 3. Конфігурація та ізоляція тестів

`TestcontainersConfiguration` створює PostgreSQL `postgres:17-alpine` через `@ServiceConnection`.

- Спільна анотація `@IntegrationTest` містить `@SpringBootTest`, `@AutoConfigureMockMvc`, профіль `test`, імпорт контейнера та WireMock-конфігурації.
- `@E2ETest` використовує `@SpringBootTest(webEnvironment = RANDOM_PORT)` і `@AutoConfigureRestTestClient`, щоб перевіряти запущений HTTP-сервер.
- У межах кожної конфігурації Spring повторно використовує тестовий контекст; конфігурації MockMvc та E2E різні.
- SQL-скрипти `reset-delivery.sql`, `reset-volunteer.sql` і `reset-lot.sql` запускаються **після кожного тестового методу** (`Sql.ExecutionPhase.AFTER_TEST_METHOD`) для очищення бізнес-таблиць.
- E2E-класи з назвами `*E2EIT` виконуються Maven Failsafe під час `verify`, щоб не обтяжувати окремий запуск `test` браузерними сценаріями.

## 4. Фактичний час виконання

**Загальний час `clean verify`: 01:23 min** (останній успішний прогін із 421 тестом). Час окремих тестових класів не наведено: для нього потрібні XML-звіти саме цього запуску в `target/surefire-reports` і `target/failsafe-reports`. Час попереднього прогону на 413 тестів не є актуальним для цієї версії.

Перші тести з новою конфігурацією Spring включають ініціалізацію контексту й контейнера PostgreSQL. Подальші тести з тим самим контекстом переважно виконуються швидше.

## 5. Підсумок `clean verify`

![Актуальний підсумок Maven verify](report-verify.png)

```text
[INFO] Tests run: 408, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time: 01:23 min
```

Перший рядок — загальний підсумок **Maven Surefire**; другий — підсумок **Maven Failsafe**. Вивід у PowerShell було відфільтровано для читабельності; сама команда виконала повний цикл `clean verify`.

## 6. Докази браузерного тестування

**Успішно пройшли 5 браузерних сценаріїв із 3 тестових класів** (`LotUiE2EIT`, `ReservationUiE2EIT`, `DestinationPointUiE2EIT`). Після прогону Playwright зберіг п'ять trace-архівів. Для здачі їх скопійовано **поза папку `target`**, до `evidence/playwright/`:

| Файл у `evidence/playwright/` | Клас | Сценарій |
|---|---|---|
| `trace-102701735871800.zip` | `DestinationPointUiE2EIT` | координатор створює пункт призначення |
| `trace-102704849080900.zip` | `LotUiE2EIT` | неправильний пароль — повідомлення про помилку |
| `trace-102705159943800.zip` | `LotUiE2EIT` | волонтер не бачить кнопки створення лота |
| `trace-102705747480200.zip` | `LotUiE2EIT` | донор створює лот і бачить його у списку |
| `trace-102709006112600.zip` | `ReservationUiE2EIT` | волонтер резервує лот через інтерфейс |

Переглянути trace можна Playwright Trace Viewer, наприклад:

```powershell
npx playwright show-trace .\evidence\playwright\trace-102701735871800.zip
```

Trace включає послідовність дій браузера, знімки станів сторінки та мережеву інформацію. Самі файли **не потрібно** запускати для оцінювання; вони є доказами вже виконаних браузерних тестів.

## 7. Відмінності та уточнення щодо бізнес-правил

- Недозволені переходи статусів лота (наприклад, повторна публікація) повертають **409 Conflict** (`InvalidLotStateException` / `ConflictException`). Порушення правил предметної області — **422 Unprocessable Entity** (`BusinessRuleException`), зокрема некоректне вікно самовивозу, код підтвердження або несумісна категорія пункту призначення.
- Скасування резервування дозволене тільки для лота у статусі `RESERVED`. Спроба скасувати резервування чернетки не повинна публікувати лот і повертає **409**.
- Для тестів використано профіль `test` із власною конфігурацією JWT і паролів демо-користувачів. Паролі не кодуються безпосередньо у тестових класах.
- Оскільки спочатку не було необхідного зовнішнього HTTP-сервісу, додано мінімальне сповіщення про завершення доставки, яке тестується за допомогою WireMock. Успішність основного процесу доставки не залежить від доступності цього сервісу.

## 8. Матеріали, що входять до комплекту для здачі

- `report-tests.md` — цей звіт із рівнями тестів, кількістю, результатами й часом.
- `report-verify.png` — скриншот останнього успішного `clean verify` (408 + 13, `BUILD SUCCESS`, `01:23 min`).
- `evidence/playwright/trace-*.zip` — **5** доказів браузерних сценаріїв.
- Початковий код і тести Spring Boot, `pom.xml`, Maven Wrapper та README.