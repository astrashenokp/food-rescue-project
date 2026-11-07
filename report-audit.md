# Звіт про аспектний аудит Food Rescue

**Дата перевірки:** 07.11.2026
**Проєкт:** Food Rescue (Spring Boot 4.1.1, Java 25, Spring Modulith)

## 1. Що зроблено

Бізнес-операції сервісного шару фіксуються аспектом. Метод, який треба записувати, позначається маркерною анотацією `@AuditAction(action = "...")`. Аспект `AuditAspect` (порада `@Around`, `@Order(1)`) перехоплює виклик, запам'ятовує користувача з контексту безпеки (`anonymous` без входу), назву методу, аргументи, час початку, виконує метод і записує результат або виняток у таблицю `audit_log`. Виняток не поглинається: після запису він прокидається далі без змін, тому відповідь API лишається тією самою, що й без аудиту (`409`, `403`, `ProblemDetail`).

Усі класи аудиту лежать у пакеті `common` без підпакетів: `AuditAction`, `AuditAspect`, `AuditLogEntity`, `AuditLogRepository`, `AuditLogWriter`.

- **Чому `@Around`.** Лише така порада бачить і аргументи, і результат, і виняток одного виклику та може виміряти його тривалість.
- **Чому `@Order(1)`, а не `HIGHEST_PRECEDENCE`.** Аспект має стояти зовні `@PreAuthorize` і `@Transactional`, щоб відмова за роллю теж потрапляла в журнал. Порядок `HIGHEST_PRECEDENCE` збігається з порядком службового перехоплювача Spring, і прив'язка `@annotation(auditAction)` ламається (`Required to bind 2 arguments, but only bound 1`).
- **Чому окремий бін `AuditLogWriter` із `REQUIRES_NEW`.** Запис аудиту виконується у власній транзакції, тому лишається, навіть коли бізнес-транзакція відкотилась. Виклик через `this` обійшов би проксі, тому запис винесено в окремий бін.
- **Чому анотація, а не рядок `execution(...)`.** Анотація видна на самому методі й не ламається при перейменуванні пакета.

Таблиця `audit_log` має стовпці `id`, `username`, `action`, `method_name`, `arguments`, `result`, `success`, `error_message`, `duration_ms`, `created_at`.

### Модуль `lot`

| Дія в журналі | Метод | Роль |
|---|---|---|
| `CREATE_LOT` | `LotService.create` | `DONOR` |
| `UPDATE_LOT` | `LotService.update` | `DONOR` |
| `PUBLISH_LOT` | `LotService.publish` | `DONOR` |
| `CANCEL_LOT` | `LotService.cancel` | `DONOR` |
| `APPROVE_LOT` | `LotService.approve` | `ADMIN` |

`delete` і читання лотів не записуються. Аргументи записуються повністю, бо `LotRequest` і `ApprovalRequest` не містять паролів, кодів чи e-mail.

## 2. Запити

Застосунок запущено командою `java -jar target/foodrescue-0.0.1-SNAPSHOT.jar` (порт 18080, H2 у файлі), запити надіслано командою `curl` під демо-акаунтами.

### Модуль `lot`

| № | Запит | Очікування | Код |
|---|---|---|---|
| 1 | `donor1`: `POST /api/v1/lots` | лот створено | `201` |
| 2 | `donor1`: `POST /api/v1/lots/{id}/publication` | лот опубліковано | `200` |
| 3 | `donor1`: `PUT /api/v1/lots/{id}` (лот уже опубліковано) | помилка бізнес-правила | `409` |
| 4 | `volunteer1`: `POST /api/v1/lots` | відмова за роллю | `403` |
| 5 | `donor1`: `POST /api/v1/lots` з порожньою назвою | помилка валідації в контролері | `400` |
| 6 | `donor1`: `POST /api/v1/lots/{id}/cancellation` | лот скасовано | `200` |
| 7 | `donor1`: `POST /api/v1/lots/{id}/cancellation` (повторно) | заборонений перехід стану | `409` |
| 8 | `admin1`: `POST /api/v1/lots/{id}/approval` | рішення модератора | `200` |

## 3. Записи в таблиці

Після цих запитів виконано:

```sql
select id, username, action, method_name, success, error_message, created_at from audit_log order by id;
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

Видно, хто (`username`), що (`action`, `method_name`), коли (`created_at`) і з яким результатом (`success`, `error_message`) виконував операцію. Записи 3, 4 і 6 доводять, що помилка бізнес-правила, відмова за роллю і заборонений перехід теж фіксуються, а відповідь API при цьому лишилась `409` і `403` (запити 3, 4 і 7), а не `500`.

Запит 5 (`400`, порожня назва) рядка в журналі не залишив: перевірка `@Valid` виконується в контролері раніше за виклик сервісу, і аспект на сервісі її не бачить.

Приклад стовпців `arguments` і `result` для запису 1 (рядки в таблиці довші, тут скорочені):

```
arguments: [LotRequest[donorOrgId=11111111-1111-1111-1111-111111111111, title=Свіжа випічка, category=BAKERY, ...
result:    LotResponse[id=cf74dda8-7fc7-44d7-8577-48b2b1545de9, donorOrgId=11111111-1111-1111-1111-111111111111, ...
```

Для запису 3 `result` дорівнює `null`, бо метод завершився винятком; `duration_ms` у записах 1 і 3 дорівнює 55 і 5 мс.

## 4. Тести

Команда `./mvnw verify`: **BUILD SUCCESS**, 420 тестів у Surefire (було 408, додано 12 тестів аудиту) і 13 у Failsafe, 0 failures, 0 errors, **01:47 min**. `ModulesTest` зелений.

**`AuditIntegrationTest`** (`common`, 3 тести, показує, що аспект пише в таблицю):

- `create_ShouldWriteSuccessRecord`: рядок із `donor1`, `success = true`, назвою методу, аргументами, результатом і часом;
- `businessRuleViolation_ShouldWriteFailedRecordAndRethrow`: помилка `LotNotDraftException` записана з `success = false`, той самий виняток вилітає з методу, назва лоту не змінилась;
- `outerTransactionRollback_ShouldKeepAuditRecord`: зовнішня транзакція відкочується, лот не створено, запис аудиту лишився.

**`LotAuditIntegrationTest`** (`lot`, 9 тестів):

- успіх: `create`, `update`, `publish`, `cancel` від `donor1` і `approve` від `admin1` записані з правильними користувачем і дією;
- помилки бізнес-правил: `update` опублікованого лоту (`LotNotDraftException`) і `cancel` зарезервованого (`InvalidLotStateException`); виняток прокинуто, стан лоту не змінився;
- помилка правила сервісу: `create` із вікном самовивозу в минулому (`InvalidPickupWindowException`), лот не створено;
- чужа роль: `create` від `volunteer1` (`AuthorizationDeniedException`), користувач `volunteer1` записаний, лот не створено.

У кожному тесті перевіряються три речі: виняток того самого типу, що й без аудиту, рядок у `audit_log` з потрібними `action`, `username`, `success` і `error_message`, а також незмінність таблиці лотів там, де операція не вдалась. Тести викликають сервіс і читають таблицю через `JdbcClient` на PostgreSQL у Testcontainers, без Mockito.

## 5. Обмеження

- Помилки `@Valid` у контролері (HTTP `400`) аудит не бачить, бо перевірка спрацьовує раніше за виклик сервісу. Помилки, які кидає сам сервіс, наприклад неприпустиме вікно самовивозу, в аудит потрапляють.
- Виклик аудитованого методу через `this` усередині того самого класу оминає проксі, тому аудит не спрацює; аудитовані методи викликаються іншими бінами.
- Якщо сама база аудиту недоступна, аспект записує `log.error`, а бізнес-виняток не підміняє; окремого тесту на це немає.
- Аудит записується лише для методів з `@AuditAction`; контролери, репозиторії та `delete` лотів не аудитуються.
