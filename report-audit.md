# Звіт про аспектний аудит Food Rescue

**Дата перевірки:** 19.11.2026  
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

19.11.2026 застосунок запущено командою `java -jar target/foodrescue-0.0.1-SNAPSHOT.jar` (порт `18080`, файлова H2), запити надіслано командою `curl` із JWT демонстраційних користувачів `donor1`, `volunteer1`, `coordinator1` і `admin1`.

| № | Запит | Результат | Код |
|---:|---|---|---|
| 1 | `donor1`: `POST /api/v1/lots` | Лот створено | `201` |
| 2 | `donor1`: `POST /api/v1/lots/{id}/publication` | Лот опубліковано | `200` |
| 3 | `donor1`: `PUT /api/v1/lots/{id}` (лот уже опубліковано) | Помилка бізнес-правила | `409` |
| 4 | `volunteer1`: `POST /api/v1/lots` | Відмова за роллю | `403` |
| 5 | `donor1`: `POST /api/v1/lots` з порожньою назвою | Помилка валідації в контролері | `400` |
| 6 | `donor1`: `POST /api/v1/lots` (лот для скасування) | Лот створено | `201` |
| 7 | `donor1`: `POST /api/v1/lots/{id}/cancellation` | Лот скасовано | `200` |
| 8 | `donor1`: `POST /api/v1/lots/{id}/cancellation` (повторно) | Заборонений перехід стану | `409` |
| 9 | `donor1`: `POST /api/v1/lots` (лот для модерації) | Лот створено | `201` |
| 10 | `admin1`: `POST /api/v1/lots/{id}/approval` | Рішення модератора | `200` |
| 11 | `volunteer1`: `POST /api/v1/lots/{id}/reservation` | Лот зарезервовано | `201` |
| 12 | `volunteer1`: `POST /api/v1/lots/{id}/reservation` (повторно) | Повторне резервування відхилено | `409` |
| 13 | `donor1`: `POST /api/v1/lots/{id}/reservation` | Відмова за роллю | `403` |
| 14 | `volunteer1`: `DELETE /api/v1/lots/{id}/reservation` | Резервування скасовано | `204` |
| 15 | `volunteer1`: `POST /api/v1/lots/{id}/reservation` (знову) | Лот зарезервовано | `201` |
| 16 | `coordinator1`: `POST /api/v1/destination-points` | Пункт призначення створено | `201` |
| 17 | `volunteer1`: `POST /api/v1/destination-points` | Відмова за роллю | `403` |
| 18 | `volunteer1`: `POST /api/v1/lots/{id}/pickup` | Лот отримано | `200` |
| 19 | `volunteer1`: `POST /api/v1/lots/{id}/pickup` (повторно) | Заборонений перехід стану | `409` |
| 20 | `donor1`: `POST /api/v1/lots/{id}/pickup` | Відмова за роллю | `403` |
| 21 | `volunteer1`: `POST /api/v1/lots/{id}/delivery` | Лот доставлено | `200` |
| 22 | `volunteer1`: `POST /api/v1/lots/{id}/delivery` (повторно) | Заборонений перехід стану | `409` |
| 23 | `volunteer1`: `POST /api/v1/lots/{id}/confirmation` (неправильний код) | Код підтвердження відхилено | `422` |
| 24 | `volunteer1`: `POST /api/v1/lots/{id}/confirmation` | Доставку підтверджено | `200` |

Запити 1-10 стосуються модуля `lot`, 11-15 модуля `volunteer`, 16-24 модуля `delivery`. Для запитів 11-24 використано лот із запиту 1: після резервування він проходить статуси `PICKED_UP`, `DELIVERED` і `CONFIRMED`. Код підтвердження для запиту 24 узято з відповіді запиту 21 і в звіті не наводиться.

## 3. Записи в таблиці

Після цих запитів виконано:

```sql
select id, username, action, method_name, success, left(error_message, 40) as error_message, created_at
from audit_log
order by id;
```

Нижче результат; текст помилки в таблиці наведено повністю (запит скорочує його до 40 символів для виводу в консоль).

| id | Запит | username | action | method_name | success | error_message | created_at |
|---:|---:|---|---|---|---|---|---|
| 1 | 1 | donor1 | CREATE_LOT | LotServiceImpl.create(..) | TRUE | null | 2026-11-19 12:17:20.122582+00 |
| 2 | 2 | donor1 | PUBLISH_LOT | LotServiceImpl.publish(..) | TRUE | null | 2026-11-19 12:17:20.439123+00 |
| 3 | 3 | donor1 | UPDATE_LOT | LotServiceImpl.update(..) | FALSE | LotNotDraftException: Лот bcea5031-4517-4df0-8ff9-04929cce26fc не в чернетці | 2026-11-19 12:17:20.704304+00 |
| 4 | 4 | volunteer1 | CREATE_LOT | LotServiceImpl.create(..) | FALSE | AuthorizationDeniedException: Access Denied | 2026-11-19 12:17:20.857396+00 |
| 5 | 6 | donor1 | CREATE_LOT | LotServiceImpl.create(..) | TRUE | null | 2026-11-19 12:17:21.204737+00 |
| 6 | 7 | donor1 | CANCEL_LOT | LotServiceImpl.cancel(..) | TRUE | null | 2026-11-19 12:17:21.43557+00 |
| 7 | 8 | donor1 | CANCEL_LOT | LotServiceImpl.cancel(..) | FALSE | InvalidLotStateException: Лот 20056e39-a02d-417c-b06a-667d7d504dce: перехід CANCELLED → CANCELLED заборонено | 2026-11-19 12:17:21.515304+00 |
| 8 | 9 | donor1 | CREATE_LOT | LotServiceImpl.create(..) | TRUE | null | 2026-11-19 12:17:21.635497+00 |
| 9 | 10 | admin1 | APPROVE_LOT | LotServiceImpl.approve(..) | TRUE | null | 2026-11-19 12:17:21.918664+00 |
| 10 | 11 | volunteer1 | RESERVE_LOT | VolunteerServiceImpl.reserve(..) | TRUE | null | 2026-11-19 12:17:22.042178+00 |
| 11 | 12 | volunteer1 | RESERVE_LOT | VolunteerServiceImpl.reserve(..) | FALSE | InvalidLotStateException: Лот bcea5031-4517-4df0-8ff9-04929cce26fc: перехід RESERVED → RESERVED заборонено | 2026-11-19 12:17:22.165797+00 |
| 12 | 13 | donor1 | RESERVE_LOT | VolunteerServiceImpl.reserve(..) | FALSE | AuthorizationDeniedException: Access Denied | 2026-11-19 12:17:22.272807+00 |
| 13 | 14 | volunteer1 | CANCEL_RESERVATION | VolunteerServiceImpl.cancelReservation(..) | TRUE | null | 2026-11-19 12:17:22.388503+00 |
| 14 | 15 | volunteer1 | RESERVE_LOT | VolunteerServiceImpl.reserve(..) | TRUE | null | 2026-11-19 12:17:22.513019+00 |
| 15 | 16 | coordinator1 | CREATE_DESTINATION_POINT | DeliveryServiceImpl.createDestinationPoint(..) | TRUE | null | 2026-11-19 12:17:22.686301+00 |
| 16 | 17 | volunteer1 | CREATE_DESTINATION_POINT | DeliveryServiceImpl.createDestinationPoint(..) | FALSE | AuthorizationDeniedException: Access Denied | 2026-11-19 12:17:22.957556+00 |
| 17 | 18 | volunteer1 | PICKUP_LOT | DeliveryServiceImpl.pickup(..) | TRUE | null | 2026-11-19 12:17:23.084419+00 |
| 18 | 19 | volunteer1 | PICKUP_LOT | DeliveryServiceImpl.pickup(..) | FALSE | InvalidLotStateException: Лот bcea5031-4517-4df0-8ff9-04929cce26fc: перехід PICKED_UP → PICKED_UP заборонено | 2026-11-19 12:17:23.2007+00 |
| 19 | 20 | donor1 | PICKUP_LOT | DeliveryServiceImpl.pickup(..) | FALSE | AuthorizationDeniedException: Access Denied | 2026-11-19 12:17:23.315782+00 |
| 20 | 21 | volunteer1 | DELIVER_LOT | DeliveryServiceImpl.deliver(..) | TRUE | null | 2026-11-19 12:17:23.432246+00 |
| 21 | 22 | volunteer1 | DELIVER_LOT | DeliveryServiceImpl.deliver(..) | FALSE | InvalidLotStateException: Лот bcea5031-4517-4df0-8ff9-04929cce26fc: перехід DELIVERED → DELIVERED заборонено | 2026-11-19 12:17:23.638875+00 |
| 22 | 23 | volunteer1 | CONFIRM_DELIVERY | DeliveryServiceImpl.confirm(..) | FALSE | InvalidConfirmationCodeException: Неправильний код підтвердження | 2026-11-19 12:17:23.744285+00 |
| 23 | 24 | volunteer1 | CONFIRM_DELIVERY | DeliveryServiceImpl.confirm(..) | TRUE | null | 2026-11-19 12:17:23.851201+00 |

Стовпець `username` вказує користувача, `action` і `method_name` дію та метод, `created_at` час її виконання, а `success` і `error_message` результат. Рядки 3, 4, 7, 11, 12, 16, 18, 19, 21 і 22 підтверджують, що помилки бізнес-правил, відмови за роллю, заборонені переходи стану та неправильний код теж фіксуються, а відповідь API при цьому лишається `409`, `403` або `422` (запити 3, 4, 8, 12, 13, 17, 19, 20, 22, 23), а не `500`.

Запит 5 (`400`, порожня назва) рядка в таблиці не створив: `@Valid` спрацьовує в контролері до виклику сервісного методу.

Приклад стовпців `arguments` і `result` для запису 1 (значення скорочено):

```text
arguments: [LotRequest[donorOrgId=11111111-1111-1111-1111-111111111111, title=Свіжа випічка, category=BAKERY, ...
result:    LotResponse[id=bcea5031-4517-4df0-8ff9-04929cce26fc, donorOrgId=11111111-1111-1111-1111-111111111111, ...
```

Для запису 3 у стовпці `result` збережено текст `null`, бо метод завершився винятком і нічого не повернув; `duration_ms` для записів 1 і 3 дорівнює 27 і 9 мс.

Код підтвердження в таблицю не потрапляє: для запису 23 (`CONFIRM_DELIVERY`) стовпець `arguments` має значення `null`, а в `result` записів 20 і 23 код замінено на `confirmationCode=[REDACTED]`. Запис 22 (неправильний код) містить лише назву винятку й повідомлення «Неправильний код підтвердження», самого коду там немає.

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
