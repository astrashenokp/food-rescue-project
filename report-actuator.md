# Звіт про Actuator і захист службових ендпоінтів Food Rescue

**Дати перевірки:** 19.11.2026, 26.11.2026  
**Проєкт:** Food Rescue (Spring Boot 4.1.1, Java 25, Spring Modulith)

## 1. Що зроблено

Підключено `spring-boot-starter-actuator`. Через веб відкрито лише два службові ендпоінти: `health` (перевірка здоров'я, публічна) і `loggers` (перегляд і зміна рівня логування, лише для адміністратора). Решта ендпоінтів Actuator не виставлена: за замовчуванням через веб відкрито тільки `health`, а все інше потрібно явно перелічувати, і ми перелічили лише те, що потрібно для завдання. Доступ до бізнес-API й сторінок цією зміною не змінено.

## 2. Конфігурація безпеки

Тепер у застосунку три ланцюги безпеки; порядок важливий, бо ланцюг сторінок не має `securityMatcher` і забирає всі запити, тому ланцюг Actuator мусить стояти перед ним.

| `@Order` | Ланцюг | Які запити бере | Автентифікація |
|---:|---|---|---|
| 0 | `actuatorFilterChain` | службові адреси Actuator (`EndpointRequest.toAnyEndpoint()`) | HTTP Basic |
| 1 | `apiFilterChain` | `/api/**` | JWT без сесій |
| 2 | `webFilterChain` | сторінки застосунку | форма входу із сесією |

Ланцюг Actuator (`SecurityConfig`):

```java
@Bean
@Order(0)
@SuppressWarnings("java:S4502")
public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
    return http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(EndpointRequest.to("health")).permitAll()
                    .anyRequest().hasRole("ADMIN"))
            .httpBasic(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .build();
}
```

Лише `health` дозволено всім, будь-яка інша відкрита службова адреса вимагає ролі `ADMIN`. CSRF у цьому ланцюгу вимкнено за прикладом лекції для керування через HTTP Basic; у ланцюгу сторінок із сесіями CSRF лишається увімкненим. Ланцюг Actuator не має явного налаштування `STATELESS`, на відміну від бізнес-API. Адміністратор це наявний обліковий запис `admin1` (роль `ADMIN`), його пароль береться з налаштування `foodrescue.demo.passwords.admin1` і перевіряється через той самий `UserDetailsService` та `Argon2PasswordEncoder`, окремого користувача чи пароля в коді не додано.

Рядки `application.properties`:

```properties
management.endpoints.web.exposure.include=health,loggers
management.endpoint.health.show-components=when-authorized
management.endpoint.health.show-details=when-authorized
management.endpoint.health.roles=ADMIN
```

`include` виставляє лише `health` і `loggers`. Параметри `show-components` і `show-details` зі значенням `when-authorized` показують технічні деталі здоров'я лише авторизованому користувачеві, а `roles=ADMIN` каже, що це роль `ADMIN`. Доступ до самих адрес визначає ланцюг безпеки, а не ці параметри.

Хто що бачить (перевірено запитами розділу 3 та тестами розділу 5):

| Запит | Без входу | `DONOR` | `ADMIN` |
|---|---|---|---|
| `GET /actuator/health` | `200`, `status` і групи, без `components` | `200`, `status` і групи, без `components` | `200`, з `components` |
| `GET /actuator/loggers` | `401` (`WWW-Authenticate: Basic`) | `403` | `200` |
| `POST /actuator/loggers/<пакет>` | `401` | `403` | `204` |
| `GET /api/v1/lots` (бізнес-API, JWT) | `401` | `200` з чинним JWT | `200` з чинним JWT |

## 3. Блокування неавторизованого доступу

Повторну перевірку виконано 26.11.2026 в Windows PowerShell за допомогою `curl.exe` для запущеного локально застосунку на порту 8080. Для службових запитів використано HTTP Basic, для бізнес-API збережено JWT-автентифікацію. Паролі введено приховано та збережено у змінних середовища поточного сеансу PowerShell:

```powershell
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host "Admin password" -AsSecureString)).Password
$env:DONOR_PASSWORD = [System.Net.NetworkCredential]::new("", (Read-Host "Donor password" -AsSecureString)).Password
```

Нижче наведено команди та фактично отримані фрагменти консольних відповідей. Незначущі HTTP-заголовки й ідентифікатори сесій опущено; для `loggers` наведено лише потрібну частину JSON (інші логери та групи опущено).

### 3.1. Перевірка здоров’я без автентифікації

```powershell
curl.exe -i http://localhost:8080/actuator/health
```

```text
HTTP/1.1 200
X-Trace-Id: 95daf865-141b-49c5-a5f2-56de917a32d8
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"groups":["liveness","readiness"],"status":"UP"}
```

Публічний ендпоінт доступний без входу. У відповіді є загальний статус `UP` і назви груп, але немає `components`.

### 3.2. Доступ до loggers без автентифікації

```powershell
curl.exe -i http://localhost:8080/actuator/loggers
```

```text
HTTP/1.1 401
X-Trace-Id: 08eade63-3126-498a-96cc-2bf0be1f6458
WWW-Authenticate: Basic realm="Realm", charset="UTF-8"
Content-Type: application/json

{"timestamp":"2026-11-26T13:28:50.333Z","status":401,"error":"Unauthorized","path":"/actuator/loggers"}
```

Сервер повернув `401 Unauthorized` і заголовок `WWW-Authenticate: Basic`. Анонімний користувач не має доступу до службового ресурсу.

### 3.3. Доступ до loggers від DONOR

```powershell
curl.exe -i -u "donor1:${env:DONOR_PASSWORD}" http://localhost:8080/actuator/loggers
```

```text
HTTP/1.1 403
X-Trace-Id: 3c9419f2-26d1-4864-a34d-1205a8f9bd4e
Content-Type: application/json

{"timestamp":"2026-11-26T13:28:58.888Z","status":403,"error":"Forbidden","path":"/actuator/loggers"}
```

Користувач `donor1` успішно автентифікований, але не має ролі `ADMIN`, тому отримав `403 Forbidden`.

### 3.4. Перевірка здоров’я від ADMIN

```powershell
curl.exe -i -u "admin1:${env:ADMIN_PASSWORD}" http://localhost:8080/actuator/health
```

```text
HTTP/1.1 200
X-Trace-Id: eb122f8f-562c-4d5c-9090-f744f72908dd
Content-Type: application/vnd.spring-boot.actuator.v3+json

{
  "components": {
    "db": {
      "details": {
        "database": "H2",
        "validationQuery": "isValid()"
      },
      "status": "UP"
    },
    "diskSpace": {
      "details": {
        "total": 1006344597504,
        "free": 2371862528,
        "threshold": 10485760,
        "path": "C:\\Users\\ACER\\Downloads\\food-rescue-project\\.",
        "exists": true
      },
      "status": "UP"
    },
    "livenessState": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    },
    "readinessState": {
      "status": "UP"
    },
    "ssl": {
      "details": {
        "expiringChains": [],
        "invalidChains": [],
        "validChains": []
      },
      "status": "UP"
    }
  },
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

Адміністратор отримав загальний статус `UP` та детальну інформацію про всі компоненти. У локальному запуску використовується H2.

### 3.5. Доступ до loggers від ADMIN

```powershell
curl.exe -i -u "admin1:${env:ADMIN_PASSWORD}" http://localhost:8080/actuator/loggers
```

```text
HTTP/1.1 200
X-Trace-Id: 11d4b212-b3a1-4685-885f-20db0f539983
Content-Type: application/vnd.spring-boot.actuator.v3+json

{
  "levels": [
    "OFF",
    "ERROR",
    "WARN",
    "INFO",
    "DEBUG",
    "TRACE"
  ],
  "loggers": {
    "ROOT": {
      "configuredLevel": "INFO",
      "effectiveLevel": "INFO"
    },
    "com.example.foodrescue.delivery": {
      "effectiveLevel": "INFO"
    },
    "com.example.foodrescue.delivery.internal.DeliveryServiceImpl": {
      "effectiveLevel": "INFO"
    }
  }
}
```

Адміністратор отримав список логерів. Початковий ефективний рівень пакета `com.example.foodrescue.delivery` та класу `DeliveryServiceImpl` — `INFO`.

### 3.6. Перевірка захисту бізнес-API

```powershell
curl.exe -i http://localhost:8080/api/v1/lots
```

```text
HTTP/1.1 401
X-Trace-Id: 7e54d0cb-24be-444b-929e-1c2d7ba229df
Content-Type: application/problem+json;charset=UTF-8

{"type":"https://api.foodrescue.local/errors/unauthorized","title":"Не автентифіковано","status":401,"detail":"Потрібна автентифікація","instance":"/api/v1/lots"}
```

Без JWT-токена захищений бізнес-ендпоінт повернув `401` у форматі ProblemDetail. Наявні правила захисту API збережено.

### Підсумок ручної перевірки

| № | Запит | Користувач | Очікуваний HTTP-код | Отриманий HTTP-код |
|---|---|---|---:|---:|
| 1 | GET `/actuator/health` | Анонімний | 200 | 200 |
| 2 | GET `/actuator/loggers` | Анонімний | 401 | 401 |
| 3 | GET `/actuator/loggers` | DONOR | 403 | 403 |
| 4 | GET `/actuator/health` | ADMIN | 200 | 200 |
| 5 | GET `/actuator/loggers` | ADMIN | 200 | 200 |
| 6 | GET `/api/v1/lots` | Анонімний | 401 | 401 |

Усі шість результатів відповідають очікуваним. Публічна перевірка здоров’я працює, деталі доступні адміністратору, а доступ до `loggers` без автентифікації або без необхідної ролі заблоковано.

## 4. Сценарій динамічної зміни рівня логування

### 4.1. Ручна перевірка модуля lot

Ручний сценарій від 19.11.2026 виконано для пакета `com.example.foodrescue.lot` (фактичний порт 18080; команди нижче наведено для стандартного порту 8080). У `LotServiceImpl.findAll` додано один діагностичний рядок `log.debug("Завантаження списку лотів")`, який виконується під час `GET /api/v1/lots`. Застосунок не перезапускається між кроками, токен `$TOKEN` отримано через `POST /api/v1/auth/login` від `donor1`.

**1. Початковий рівень:**

```text
$ curl -u admin1:$ADMIN_PASSWORD http://localhost:8080/actuator/loggers/com.example.foodrescue.lot
{"effectiveLevel":"INFO"}
```

**2. Виклик API при рівні `INFO`.** У консолі з'явились лише повідомлення рівня `INFO` та вище, рядка `DEBUG` немає:

```text
$ curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/lots   -> HTTP 200

Консоль після виклику (рівень INFO):
(нових рядків рівня INFO чи DEBUG немає)
```

**3. Зміна рівня на `DEBUG`:**

```text
$ curl -u admin1:$ADMIN_PASSWORD -X POST -H 'Content-Type: application/json' -d @level-debug.json http://localhost:8080/actuator/loggers/com.example.foodrescue.lot
HTTP 204

$ curl -u admin1:$ADMIN_PASSWORD http://localhost:8080/actuator/loggers/com.example.foodrescue.lot
{"configuredLevel":"DEBUG","effectiveLevel":"DEBUG"}
```

У файлі `level-debug.json` лежить `{"configuredLevel":"DEBUG"}`.

**4. Той самий виклик API при рівні `DEBUG`.** У консолі з'явився діагностичний рядок:

```text
$ curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/lots   -> HTTP 200

Консоль після виклику (рівень DEBUG):
2026-11-19 20:16:13,328 DEBUG [488bae7c-54b8-40da-8db1-5f7c3f684ebb] [http-nio-18080-exec-5] c.e.f.lot.internal.LotServiceImpl - Завантаження списку лотів
```

**5. Скидання рівня:**

```text
$ curl -u admin1:$ADMIN_PASSWORD -X POST -H 'Content-Type: application/json' -d @level-null.json http://localhost:8080/actuator/loggers/com.example.foodrescue.lot
HTTP 204

$ curl -u admin1:$ADMIN_PASSWORD http://localhost:8080/actuator/loggers/com.example.foodrescue.lot
{"effectiveLevel":"INFO"}

$ curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/lots   -> HTTP 200

Консоль після виклику (рівень скинуто):
(нових рядків рівня INFO чи DEBUG немає)
```

У файлі `level-null.json` лежить `{"configuredLevel":null}`. Після скидання відповідь містить лише `effectiveLevel`, поля `configuredLevel` немає. Діагностичний рядок з'являється після зміни рівня на `DEBUG` і зникає після скидання, застосунок при цьому не перезапускався; зміна діє до наступного перезапуску.

### 4.2. Діагностичне логування модуля delivery

У `DeliveryServiceImpl.getDestinationPoint(UUID id)` додано `log.debug("Завантаження пункту призначення")`. Повідомлення не містить персональних даних, бізнес-логіку методу не змінено. Рівень пакета `com.example.foodrescue.delivery` змінюється через `/actuator/loggers/com.example.foodrescue.delivery`. Сценарій INFO → DEBUG → INFO перевірено автоматизовано без перезапуску контексту (див. розділ 5).

## 5. Тести

Остання повна перевірка на 26.11.2026: `.\mvnw.cmd clean verify` — **BUILD SUCCESS**, 452 тести Surefire і 13 тестів Failsafe, разом **465 тестів**, 0 failures, 0 errors, 0 skipped, **01:33 min**. Тести виконувалися з Java 25.0.4.1 на PostgreSQL 17 у Testcontainers; `ModulesTest` також пройшов.

```text
[INFO] Tests run: 452, Failures: 0, Errors: 0, Skipped: 0
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  01:33 min
[INFO] Finished at: 2026-11-26T15:18:01+02:00
```

Рядки підсумків Surefire та Failsafe наведено разом. Попередня перевірка від 19.11.2026: 463 тести, BUILD SUCCESS (до додавання двох тестів delivery).

**`ActuatorSecurityIntegrationTest`** (`common`, 13 тестів):

- `health_WhenAnonymous_ShouldReturnUpWithoutComponents`: `200`, `status=UP`, `components` немає;
- `health_WhenAdmin_ShouldReturnComponents`: `200`, `components.ping.status=UP`;
- `loggers_WhenAnonymous_ShouldReturnUnauthorized`: `401`;
- `loggers_WhenDonor_ShouldReturnForbidden`: `403`;
- `loggers_WhenAdminWithPassword_ShouldReturnOk`: справжній Basic-вхід `admin1`, `200`;
- `loggers_WhenDonorWithPassword_ShouldReturnForbidden`: справжній Basic-вхід `donor1`, `403`;
- `loggers_WhenWrongPassword_ShouldReturnUnauthorized`: невірний пароль, `401`;
- `setLevel_WhenAnonymous_ShouldReturnUnauthorized`: `401`;
- `setLevel_WhenDonor_ShouldReturnForbiddenAndKeepLevel`: `403`, рівень лишається `INFO`;
- `env_WhenNotExposedAndAnonymous_ShouldRedirectToLogin`: невиставлений ендпоінт, `302` на `/login`;
- `env_WhenNotExposedAndAdmin_ShouldReturnNotFound`: `404`;
- `businessApi_WhenAnonymous_ShouldStayProtected`: `/api/v1/lots` без токена, `401`;
- `webPage_WhenAnonymous_ShouldRedirectToLogin`: `/lots` без входу, `302` на `/login`.

**`LotLogLevelIntegrationTest`** (`lot`, 2 тести): `setLevel_WhenDebug_ShouldShowDebugMessageWithoutRestart` (до зміни рівня рядка `DEBUG` у консолі немає, після зміни він з'являється без перезапуску) і `setLevel_WhenResetToNull_ShouldHideDebugMessage` (після скидання рядок не з'являється). Після кожного тесту рівень скидається, тому інші тести не бачать `DEBUG`. Запити до `loggers` у тестах виконує спільний `ActuatorTestSupport`, паролі беруться з налаштувань.

**`DeliveryLogLevelIntegrationTest`** (`delivery`, 2 тести). Запити до Actuator виконуються через спільний `ActuatorTestSupport`, а повідомлення консолі перевіряються за допомогою `OutputCaptureExtension` та `CapturedOutput`.

- `setLevel_WhenDebug_ShouldShowDebugMessageWithoutRestart`: перевіряється початковий рівень `INFO`, виконується GET-запит до `/api/v1/destination-points/{id}` і підтверджується відсутність діагностичного повідомлення. Далі через Actuator встановлюється `DEBUG`, перевіряється ефективний рівень та повторюється той самий GET-запит. Повідомлення з’являється без перезапуску застосунку.
- `setLevel_WhenResetToNull_ShouldHideDebugMessage`: після встановлення `DEBUG` рівень скидається через `{"configuredLevel":null}`. Перевіряється повернення ефективного рівня до `INFO` та відсутність діагностичного повідомлення при наступному GET-запиті.

Обидва тести перевіряють успішність бізнес-запиту (`200`) та ідентифікатор отриманого пункту призначення. Після кожного тесту рівень логування скидається через `@AfterEach`.

Команда запуску:

```powershell
.\mvnw.cmd "-Dtest=DeliveryLogLevelIntegrationTest" test
```

Фактичний фрагмент журналу:

```text
2026-11-26 15:16:15,724 DEBUG [b82335e7-a7d0-4480-99d9-c3182ee15cf2] [main] c.e.f.d.internal.DeliveryServiceImpl - Завантаження пункту призначення
```

Результат тестування:

```text
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  28.254 s
[INFO] Finished at: 2026-11-26T15:16:16+02:00
```

## 6. Обмеження

- Невиставлені ендпоінти (`env`, `beans` тощо) не віддаються: анонімний запит отримує перенаправлення `302` на `/login`, бо потрапляє до ланцюга сторінок, а запит з Basic-входом `admin1` теж `302`, бо ланцюг сторінок Basic не читає. Автентифікований у ланцюгу сторінок запит отримує `404`.
- Помилки службових адрес мають стандартний JSON Spring Boot, а не `ProblemDetail`, як бізнес-API: окремих обробників помилок для Actuator не додавали.
- Службові адреси працюють на тому самому порту, що й застосунок. Перед публічним доступом (наприклад, через ngrok) пароль `admin1` треба замінити складним через змінну середовища `FOODRESCUE_DEMO_PASSWORDS_ADMIN1`.
- Рівень логування, змінений через `loggers`, діє до перезапуску застосунку.

## 7. Висновок

Ручні запити від 26.11.2026 підтвердили правильне розмежування доступу до `health` і `loggers` та збереження захисту бізнес-API. Ручний сценарій lot і автоматизовані сценарії lot та delivery підтвердили динамічну зміну рівня логування без перезапуску. У перевіреній версії проєкту пройшли всі 465 тестів.
