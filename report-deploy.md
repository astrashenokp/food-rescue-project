# Звіт про розгортання Food Rescue

## 1. Обрана архітектура

Застосунок розгортається в ізольованому Docker-середовищі через Docker Compose на локальній машині (Windows, Docker Desktop). Docker дозволений завданням, образ однаково збирається й запускається на будь-якій машині, а процес застосунку відокремлений від системи й працює без прав root.

```
клієнт → (тунель або публічна адреса) → контейнер app (порт 8080) → Spring Boot → H2 в пам'яті
```

Контейнер збирається багатоетапним `Dockerfile`: на першому етапі образ `eclipse-temurin:25-jdk` збирає jar командою `./mvnw -B -DskipTests package`, на другому образ `eclipse-temurin:25-jre` містить лише jar. Тести в збірку образу не входять (інтеграційним тестам потрібен Docker усередині збірки), їх запускають окремо командою `./mvnw verify`.

## 2. Файли налаштувань

Усі файли лежать у корені репозиторію. Справжні секрети в репозиторій не потрапляють: `deploy.env` додано в `.gitignore` і `.dockerignore`, у репозиторії лише шаблон `deploy.env.example`.

**`Dockerfile`**

```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /opt/foodrescue
RUN mkdir logs && chown app:app logs
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Системний користувач `app` запускає процес замість root. Каталог `logs` створюється заздалегідь і передається цьому користувачу, бо `logback-spring.xml` пише журнал у `logs/foodrescue.log`, а без цього каталогу застосунок не стартує.

**`.dockerignore`**

```
target
deploy.env
.git
```

**`compose.yaml`**

```yaml
services:
  app:
    build: .
    image: foodrescue:latest
    env_file: deploy.env
    ports:
      - "8080:8080"
    restart: unless-stopped
    stop_grace_period: 40s
```

`env_file` передає секрети змінними середовища, `restart: unless-stopped` вмикає автозапуск, а `stop_grace_period` (40 с) більший за час очікування Spring (30 с), щоб Docker не вбив контейнер раніше, ніж завершаться активні запити.

**`deploy.env.example`** (шаблон без справжніх значень; копія `deploy.env` у `.gitignore`)

```
JWT_SECRET=<random string of at least 32 bytes>
FOODRESCUE_DEMO_PASSWORDS_DONOR1=<password>
FOODRESCUE_DEMO_PASSWORDS_VOLUNTEER1=<password>
FOODRESCUE_DEMO_PASSWORDS_COORDINATOR1=<password>
FOODRESCUE_DEMO_PASSWORDS_ADMIN1=<password>
```

`JWT_SECRET` перевизначає `security.jwt.secret`, а змінні `FOODRESCUE_DEMO_PASSWORDS_*` перевизначають паролі демо-акаунтів `foodrescue.demo.passwords.*` з `application.properties`.

**`application.properties`** (властивості безпечного завершення)

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```
