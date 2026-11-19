# Звіт про аспектний аудит Food Rescue

**Дати перевірки:** 07.11.2026 (запити до API та записи `lot`), 19.11.2026 (інтеграційні й E2E-тести)  
**Проєкт:** Food Rescue (Spring Boot 4.1.1, Java 25, Spring Modulith)

## 1. Що зроблено

Бізнес-операції сервісного шару фіксуються аспектом. Метод, який потрібно записувати, позначається анотацією `@AuditAction(action = "...")`. Аспект `AuditAspect` (порада `@Around`, `@Order(1)`) перехоплює виклик, отримує користувача з контексту безпеки (`anonymous`, якщо автентифікації немає), назву методу, аргументи й час початку. Після виконання методу він записує результат або інформацію про виняток у таблицю `audit_log`, а також тривалість операції. Оригінальний виняток не поглинається й передається далі, тому обробка помилок API залишається незмінною (`409`, `403`, `ProblemDetail`).

Класи аудиту розміщені в пакеті `common`: `AuditAction`, `AuditAspect`, `AuditLogEntity`, `AuditLogRepository`, `AuditLogWriter`.

- **`@Around`** дає змогу отримати аргументи, результат або виняток одного виклику й виміряти час виконання.
- **`@Order(1)`** дозволяє аспекту перехоплювати також відмови `@PreAuthorize` та виконуватися перед транзакційним перехоплювачем.
- **`AuditLogWriter` із `REQUIRES_NEW`** зберігає аудит в окремій транзакції: запис залишається навіть після відкату бізнес-транзакції. Окремий бін потрібний, щоб виклик проходив через Spring-проксі.
- **`@AuditAction`** указує, які саме сервісні операції потрібно аудитувати, без дублювання коду журналювання в кожному методі.

Таблиця `audit_log` містить стовпці `id`, `username`, `action`, `method_name`, `arguments`, `result`, `success`, `error_message`, `duration_ms`, `created_at`.

### Модуль `lot`

| Дія в журналі | Метод | Роль |
|---|---|---|
| `CREATE_LOT` | `LotService.create` | `DONOR` |
| `UPDATE_LOT` | `LotService.update` | `DONOR` |
| `PUBLISH_LOT` | `LotService.publish` | `DONOR` |
| `CANCEL_LOT` | `LotService.cancel` | `DONOR` |
| `APPROVE_LOT` | `LotService.approve` | `ADMIN` |

`delete` і читання лотів не аудитуються. Аргументи `LotRequest` та `ApprovalRequest` не містять паролів чи кодів підтвердження.

### Модуль `volunteer`

| Дія в журналі | Метод | Роль |
|---|---|---|
| `RESERVE_LOT` | `VolunteerService.reserve` | `VOLUNTEER` |
| `CANCEL_RESERVATION` | `VolunteerService.cancelReservation` | `VOLUNTEER` |

Записується UUID лоту. Валідація `@NotNull` для `reserve` працює на рівні сервісу через `@Validated`, тому помилка `ConstraintViolationException` також фіксується аспектом.

### Модуль `delivery`

| Дія в журналі | Метод | Роль |
|---|---|---|
| `CREATE_DESTINATION_POINT` | `DeliveryService.createDestinationPoint` | `COORDINATOR` |
| `PICKUP_LOT` | `DeliveryService.pickup` | `VOLUNTEER` |
| `DELIVER_LOT` | `DeliveryService.deliver` | `VOLUNTEER` |
| `CONFIRM_DELIVERY` | `DeliveryService.confirm` | `VOLUNTEER` |

Для `confirm` використано `logParameters = false`, щоб не записувати код підтвердження з аргументів. У текстовому представленні `DeliveryResponse` код замінюється на `[REDACTED]`, тому він також не потрапляє до стовпця `result`. Для `createDestinationPoint` перевіряється сервісна валідація вхідних даних.

## 2. Запити

### Модуль `lot`

07.11.2026 застосунок запущено командою `java -jar target/foodrescue-0.0.1-SNAPSHOT.jar` (порт `18080`, файлова H2); запити надіслано через `curl` під демонстраційними користувачами.

| № | Запит | Результат | Код |
|---|---|---|---|
| 1 | `donor1`: `POST /api/v1/lots` | Лот створено | `201` |
| 2 | `donor1`: `POST /api/v1/lots/{id}/publication` | Лот опубліковано | `200` |
| 3 | `donor1`: `PUT /api/v1/lots/{id}` (лот уже опубліковано) | Помилка бізнес-правила | `409` |
| 4 | `volunteer1`: `POST /api/v1/lots` | Відмова за роллю | `403` |
| 5 | `donor1`: `POST /api/v1/lots` з порожньою назвою | Помилка валідації в контролері | `400` |
| 6 | `donor1`: `POST /api/v1/lots/{id}/cancellation` | Лот скасовано | `200` |
| 7 | `donor1`: `POST /api/v1/lots/{id}/cancellation` (повторно) | Заборонений перехід стану | `409` |
| 8 | `admin1`: `POST /api/v1/lots/{id}/approval` | Рішення модератора | `200` |

### Модуль `volunteer`

19.11.2026 запити перевірено в `ReservationApiE2EIT`: запуск HTTP API з JWT користувачів і перевірка стану лота в PostgreSQL.

| Користувач | Запит | Результат | Код |
|---|---|---|---|
| `volunteer1` | `POST /api/v1/lots/{id}/reservation` | Опублікований лот зарезервовано | `201` |
| `volunteer1` | Повторний `POST /api/v1/lots/{id}/reservation` | Повторне резервування відхилено | `409` |
| `volunteer1` | `DELETE /api/v1/lots/{id}/reservation` | Резервування скасовано | `204` |
| `donor1` | `POST /api/v1/lots/{id}/reservation` | Відмова за роллю | `403` |

Після резервування лот має статус `RESERVED` і містить ідентифікатор волонтера; після скасування повертається в `PUBLISHED` без резервування.

### Модуль `delivery`

19.11.2026 перевірено повний HTTP-сценарій у `DeliveryFlowE2EIT`: створення пункту призначення, створення й публікація лота, резервування, отримання, доставка та підтвердження. У тесті використовуються JWT користувачів і PostgreSQL.

| Користувач | Запит | Результат | Код |
|---|---|---|---|
| `coordinator1` | `POST /api/v1/destination-points` | Пункт створено | `201` |
| `donor1` | `POST /api/v1/lots`, потім `POST /api/v1/lots/{id}/publication` | Лот створено й опубліковано | `201`, `200` |
| `volunteer1` | `POST /api/v1/lots/{id}/reservation` | Лот зарезервовано | `201` |
| `volunteer1` | `POST /api/v1/lots/{id}/pickup` | Статус `PICKED_UP` | `200` |
| `volunteer1` | `POST /api/v1/lots/{id}/delivery` | Статус `DELIVERED` | `200` |
| `volunteer1` | `POST /api/v1/lots/{id}/confirmation` | Статус `CONFIRMED` | `200` |
| `volunteer1` | `POST /api/v1/destination-points` | Відмова за роллю | `403` |

Для отримання передано `{"actualWeightKg":5}`, для доставки — ідентифікатор пункту призначення, для підтвердження — фактичний код і `receivedWeightKg = 5`. У тесті перевірено кінцевий статус `CONFIRMED`, збережену вагу й пункт призначення. Код підтвердження у звіті не наводиться.

## 3. Записи в таблиці

Після запитів до `lot` від 07.11.2026 виконано:

```sql
select id, username, action, method_name, success, error_message, created_at
from audit_log
order by id;
```

| id | username | action | method_name | success | error_message | created_at |
|---:|---|---|---|---|---|---|
| 1 | donor1 | CREATE_LOT | LotServiceImpl.create(..) | TRUE | null | 2026-11-07 17:39:17.146926+00 |
| 2 | donor1 | PUBLISH_LOT | LotServiceImpl.publish(..) | TRUE | null | 2026-11-07 17:39:17.49946+00 |
| 3 | donor1 | UPDATE_LOT | LotServiceImpl.update(..) | FALSE | LotNotDraftException: Лот cf74dda8-7fc7-44d7-8577-48b2b1545de9 не в чернетці | 2026-11-07 17:39:17.7786+00 |
| 4 | volunteer1 | CREATE_LOT | LotServiceImpl.create(..) | FALSE | AuthorizationDeniedException: Access Denied | 2026-11-07 17:39:17.912647+00 |
| 5 | donor1 | CANCEL_LOT | LotServiceImpl.cancel(..) | TRUE | null | 2026-11-07 17:39:18.180742+00 |
| 6 | donor1 | CANCEL_LOT | LotServiceImpl.cancel(..) | FALSE | InvalidLotStateException: Лот cf74dda8-7fc7-44d7-8577-48b2b1545de9: перехід CANCELLED → CANCELLED заборонено | 2026-11-07 17:39:18.279361+00 |
| 7 | donor1 | CREATE_LOT | LotServiceImpl.create(..) | TRUE | null | 2026-11-07 17:39:18.403822+00 |
| 8 | admin1 | APPROVE_LOT | LotServiceImpl.approve(..) | TRUE | null | 2026-11-07 17:39:18.632441+00 |

Стовпець `username` вказує користувача, `action` і `method_name` — дію та метод, `created_at` — час її виконання, а `success` і `error_message` — результат. Записи 3, 4 і 6 підтверджують аудит помилки бізнес-правила, відмови за роллю та недозволеного переходу; відповідні HTTP-відповіді залишилися `409` або `403`, а не `500`.

Запит № 5 (`400`, порожня назва) запису в таблиці не створив: `@Valid` спрацювала в контролері до виклику сервісного методу.

Приклад `arguments` і `result` для запису № 1 (значення скорочено):

```text
arguments: [LotRequest[donorOrgId=11111111-1111-1111-1111-111111111111, title=Свіжа випічка, category=BAKERY, ...
result:    LotResponse[id=cf74dda8-7fc7-44d7-8577-48b2b1545de9, donorOrgId=11111111-1111-1111-1111-111111111111, ...
```

Для запису № 3 у стовпці result збережено рядок "null", оскільки метод завершився винятком і не повернув результату. duration_ms для записів № 1 і № 3 — 55 і 5 мс відповідно.

### Записи `volunteer` і `delivery`

У тестах від 19.11.2026 реальні записи `audit_log` читаються через `JdbcClient` і перевіряються за користувачем, дією, успішністю та повідомленням про виняток. Нижче наведено перевірені значення цих полів. На відміну від таблиці `lot` вище, тут не наведено згенеровані `id` й точні `created_at`: їхні конкретні значення не виводилися в консоль, а в тестах перевірялася наявність часу запису.

| Сценарій | username | action | success | error_message |
|---|---|---|---|---|
| Резервування | volunteer1 | `RESERVE_LOT` | TRUE | null |
| Скасування резервування | volunteer1 | `CANCEL_RESERVATION` | TRUE | null |
| Повторне резервування | volunteer1 | `RESERVE_LOT` | FALSE | `InvalidLotStateException` |
| UUID лота дорівнює `null` | volunteer1 | `RESERVE_LOT` | FALSE | `ConstraintViolationException` |
| Резервування від `DONOR` | donor1 | `RESERVE_LOT` | FALSE | `AuthorizationDeniedException` |
| Створення пункту | coordinator1 | `CREATE_DESTINATION_POINT` | TRUE | null |
| Отримання лота | volunteer1 | `PICKUP_LOT` | TRUE | null |
| Доставка | volunteer1 | `DELIVER_LOT` | TRUE | null |
| Підтвердження | volunteer1 | `CONFIRM_DELIVERY` | TRUE | null |
| Повторне отримання | volunteer1 | `PICKUP_LOT` | FALSE | `InvalidLotStateException` |
| Повторна доставка | volunteer1 | `DELIVER_LOT` | FALSE | `InvalidLotStateException` |
| Неправильний код | volunteer1 | `CONFIRM_DELIVERY` | FALSE | `InvalidConfirmationCodeException` |
| Некоректний запит пункту | coordinator1 | `CREATE_DESTINATION_POINT` | FALSE | `ConstraintViolationException` |
| Створення пункту від `DONOR` | donor1 | `CREATE_DESTINATION_POINT` | FALSE | `AuthorizationDeniedException` |
| Отримання лота від `DONOR` | donor1 | `PICKUP_LOT` | FALSE | `AuthorizationDeniedException` |

Для успішного `DELIVER_LOT` тест перевіряє наявність `lotStatus=DELIVERED` і `confirmationCode=[REDACTED]` у `result`; для `CONFIRM_DELIVERY` — `arguments = null`, `lotStatus=CONFIRMED` і прихований код у `result`. Помилка неправильного коду записується без розкриття самого коду. Після невдалих переходів стан бізнес-даних перевіряється окремими SQL-запитами.

## 4. Тести

19.11.2026 виконано:

```powershell
.\mvnw.cmd clean verify
```

**Результат: `BUILD SUCCESS`** — 435 тестів Surefire та 13 тестів Failsafe, разом **448 тестів**; **0 failures, 0 errors, 0 skipped**. Час виконання — **01:16 min**. Тести виконувалися на Java 25 з PostgreSQL 17 у Testcontainers. `ModulesTest` також пройшов.

**`AuditIntegrationTest`** (`common`, 3 тести):

- `create_ShouldWriteSuccessRecord` — перевірка успішного запису з користувачем, методом, аргументами, результатом і часом;
- `businessRuleViolation_ShouldWriteFailedRecordAndRethrow` — запис помилки `LotNotDraftException`, передача винятку далі й незмінність даних лота;
- `outerTransactionRollback_ShouldKeepAuditRecord` — відкат бізнес-транзакції без втрати запису аудиту.

**`LotAuditIntegrationTest`** (`lot`, 9 тестів): успішні `create`, `update`, `publish`, `cancel`, `approve`; помилки `LotNotDraftException`, `InvalidLotStateException`, `InvalidPickupWindowException`; відмова `AuthorizationDeniedException`. Перевіряються записи в `audit_log` і незмінність бізнес-даних у разі помилки.

**`VolunteerAuditIntegrationTest`** (`volunteer`, 5 тестів): успішне резервування й скасування, повторне резервування, валідація `null` і відмова користувачу з роллю `DONOR`. Перевіряються `audit_log` та стан лота.

**`DeliveryAuditIntegrationTest`** (`delivery`, 10 тестів): створення пункту призначення, отримання лота, доставка та підтвердження; повторні `pickup` і `deliver`; неправильний код підтвердження; сервісна валідація; дві відмови за роллю. Перевіряються записи аудиту, стани лота та доставки й приховування коду підтвердження.

Загалом виконано **27 інтеграційних тестів підсистеми аудиту** (`3 + 9 + 5 + 10`). Вони викликають сервіси Spring і перевіряють вміст таблиці `audit_log` через `JdbcClient`; E2E-тести додатково виконують справжні HTTP-запити через запущений застосунок.

## 5. Обмеження

- Помилки `@Valid` у контролері (HTTP `400`) аудит не бачить, оскільки перевірка виконується до виклику сервісу. Помилки бізнес-правил і сервісної валідації, які виникають під час виклику аудитованого сервісного методу, зберігаються в журналі.
- Самовиклик `this.method()` усередині одного класу обходить Spring AOP-проксі; аудитовані методи викликаються через Spring-біни.
- Якщо запис до бази аудиту не вдається, `AuditAspect` пише `log.error` і не замінює оригінальний бізнес-виняток. Окремого тесту на недоступність бази немає.
- Аудит виконується для методів, позначених `@AuditAction`; читання, репозиторії, контролери й видалення лотів не охоплені цим аспектом.
