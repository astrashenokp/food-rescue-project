# Звіт про Actuator і захист службових ендпоінтів Food Rescue

**Дата перевірки:** 19.11.2026  
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

Лише `health` дозволено всім, будь-який інший службовий адрес вимагає ролі `ADMIN`. CSRF у цьому ланцюгу вимкнено, бо доступ іде через заголовок `Authorization` (Basic) без сесії і cookie; у ланцюгу сторінок із сесіями CSRF лишається увімкненим. Адміністратор це наявний обліковий запис `admin1` (роль `ADMIN`), його пароль береться з налаштування `foodrescue.demo.passwords.admin1` і перевіряється через той самий `UserDetailsService` та `Argon2PasswordEncoder`, окремого користувача чи пароля в коді не додано.

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
| `GET /actuator/health` | `200`, лише `status` | `200`, лише `status` | `200`, з `components` |
| `GET /actuator/loggers` | `401` (`WWW-Authenticate: Basic`) | `403` | `200` |
| `POST /actuator/loggers/<пакет>` | `401` | `403` | `204` |
| `GET /api/v1/lots` (бізнес-API, JWT) | `401` | `200` | `200` |

## 3. Блокування неавторизованого доступу

Перевірку виконано 19.11.2026 на запущеному застосунку (`java -jar`, без профілю `dev`, порт 18080); нижче команди записано для порту 8080, а паролі беруться зі змінних середовища `ADMIN_PASSWORD` і `DONOR_PASSWORD` (значення `foodrescue.demo.passwords.admin1` і `donor1`).

```text
$ curl -i http://localhost:8080/actuator/health
HTTP/1.1 200
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"groups":["liveness","readiness"],"status":"UP"}

$ curl -i http://localhost:8080/actuator/loggers
HTTP/1.1 401
WWW-Authenticate: Basic realm="Realm", charset="UTF-8"

{"timestamp":"2026-11-19T18:16:08.890Z","status":401,"error":"Unauthorized","path":"/actuator/loggers"}

$ curl -i -u donor1:$DONOR_PASSWORD http://localhost:8080/actuator/loggers
HTTP/1.1 403

{"timestamp":"2026-11-19T18:16:09.258Z","status":403,"error":"Forbidden","path":"/actuator/loggers"}

$ curl -i -u admin1:$ADMIN_PASSWORD http://localhost:8080/actuator/health
HTTP/1.1 200

{"components":{"db":{"details":{"database":"H2","validationQuery":"isValid()"},"status":"UP"},"diskSpace":{...},"livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},...}

$ curl -u admin1:$ADMIN_PASSWORD -o /dev/null -w "HTTP %{http_code}" http://localhost:8080/actuator/loggers
HTTP 200

$ curl -i http://localhost:8080/api/v1/lots
HTTP/1.1 401

{"type":"https://api.foodrescue.local/errors/unauthorized","title":"Не автентифіковано","status":401,"detail":"Потрібна автентифікація","instance":"/api/v1/lots"}
```

Результат за порядком: `health` без входу `200` без `components`; `loggers` без входу `401` із заголовком `WWW-Authenticate: Basic`; `donor1` отримує `403`; `admin1` отримує `health` із `components` і `loggers` з кодом `200`; бізнес-API без токена лишається `401` у форматі `ProblemDetail`, правила `/api/**` не змінилися.

## 4. Сценарій динамічної зміни рівня логування

Для сценарію вибрано пакет `com.example.foodrescue.lot`. У `LotServiceImpl.findAll` додано один діагностичний рядок `log.debug("Завантаження списку лотів")`, який виконується під час `GET /api/v1/lots`. Застосунок не перезапускається між кроками, токен `$TOKEN` отримано через `POST /api/v1/auth/login` від `donor1`.

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

## 5. Тести

Команда `./mvnw verify` (19.11.2026): **BUILD SUCCESS**, 450 тестів Surefire і 13 тестів Failsafe, разом **463 тести**, 0 failures, 0 errors, 0 skipped, **02:19 min**. Тести виконувалися на PostgreSQL 17 у Testcontainers, `ModulesTest` також пройшов.

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

## 6. Обмеження

- Невиставлені ендпоінти (`env`, `beans` тощо) не віддаються: анонімний запит отримує перенаправлення `302` на `/login`, бо потрапляє до ланцюга сторінок, а запит з Basic-входом `admin1` теж `302`, бо ланцюг сторінок Basic не читає. Автентифікований у ланцюгу сторінок запит отримує `404`.
- Помилки службових адрес мають стандартний JSON Spring Boot, а не `ProblemDetail`, як бізнес-API: окремих обробників помилок для Actuator не додавали.
- Службові адреси працюють на тому самому порту, що й застосунок. Перед публічним доступом (наприклад, через ngrok) пароль `admin1` треба замінити складним через змінну середовища `FOODRESCUE_DEMO_PASSWORDS_ADMIN1`.
- Рівень логування, змінений через `loggers`, діє до перезапуску застосунку.
