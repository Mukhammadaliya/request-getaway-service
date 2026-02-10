# Open-Source Publication Action Plan

## Request Gateway Service — План подготовки к публикации

**Дата аудита:** 2026-02-10
**Текущая оценка готовности:** 3.5 / 10
**Целевая оценка:** 8+ / 10

---

## Оглавление

- [Фаза 0: Критические блокеры безопасности](#фаза-0-критические-блокеры-безопасности)
- [Фаза 1: Юридические и организационные файлы](#фаза-1-юридические-и-организационные-файлы)
- [Фаза 2: Документация (README)](#фаза-2-документация-readme)
- [Фаза 3: Очистка кода](#фаза-3-очистка-кода)
- [Фаза 4: Тестирование](#фаза-4-тестирование)
- [Фаза 5: CI/CD и инструменты качества](#фаза-5-cicd-и-инструменты-качества)
- [Фаза 6: Финальная полировка](#фаза-6-финальная-полировка)
- [Приложения](#приложения)

---

## Фаза 0: Критические блокеры безопасности

> **ПРИОРИТЕТ: МАКСИМАЛЬНЫЙ. Без выполнения этой фазы публикация ЗАПРЕЩЕНА.**

### Задача 0.1: Ротация всех скомпрометированных credentials

Следующие секреты уже находятся в git-истории и должны быть **немедленно заменены** на стороне серверов, вне зависимости от публикации:

| Секрет | Файл | Строка | Текущее значение |
|--------|------|--------|-----------------|
| Biruni password | `src/main/resources/application.yml` | 73 | `greenwhite` |
| OAuth2 client-id | `src/main/resources/application.yml` | 91 | `12344432421` |
| OAuth2 client-secret | `src/main/resources/application.yml` | 92 | `ASLKDGHAJGF1341ASGHFAGFHj` |
| Grafana password | `docker-compose.yml` | 145 | `admin123` |

**Действия:**

```bash
# 1. На стороне Biruni-сервера — сменить пароль для пользователя admin@head
# 2. На стороне OAuth2-сервера — перегенерировать client-secret для клиента 12344432421
# 3. Обновить Grafana-пароль в production-окружениях
```

### Задача 0.2: Экстернализация секретов из конфигурации

**Файл:** `src/main/resources/application.yml`

Заменить захардкоженные значения на environment variables:

```yaml
# БЫЛО (строки 70-76):
gateway:
  biruni:
    base-url: http://localhost:8080/smartup24
    username: admin@head
    password: greenwhite
    request-pull-uri: /b/biruni/bmb/requests$pull
    response-save-uri: /b/biruni/bmb/requests$save
    connection-timeout: 60

# ДОЛЖНО СТАТЬ:
gateway:
  biruni:
    base-url: ${BIRUNI_BASE_URL:http://localhost:8080/smartup24}
    username: ${BIRUNI_USERNAME:}
    password: ${BIRUNI_PASSWORD:}
    request-pull-uri: ${BIRUNI_PULL_URI:/b/biruni/bmb/requests$pull}
    response-save-uri: ${BIRUNI_SAVE_URI:/b/biruni/bmb/requests$save}
    connection-timeout: ${BIRUNI_CONNECTION_TIMEOUT:60}
```

```yaml
# БЫЛО (строки 86-93):
gateway:
  oauth2:
    providers:
      smartup:
        type: biruni
        token-url: http://localhost:8080/b6/security/oauth/token
        client-id: 12344432421
        client-secret: ASLKDGHAJGF1341ASGHFAGFHj
        scope: read+write

# ДОЛЖНО СТАТЬ:
gateway:
  oauth2:
    providers:
      smartup:
        type: biruni
        token-url: ${OAUTH2_SMARTUP_TOKEN_URL:http://localhost:8080/b6/security/oauth/token}
        client-id: ${OAUTH2_SMARTUP_CLIENT_ID:}
        client-secret: ${OAUTH2_SMARTUP_CLIENT_SECRET:}
        scope: ${OAUTH2_SMARTUP_SCOPE:read+write}
```

**Файл:** `docker-compose.yml`

```yaml
# БЫЛО (строки 143-146):
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin123

# ДОЛЖНО СТАТЬ:
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}
```

### Задача 0.3: Создать .env.example

**Создать файл:** `.env.example`

```properties
# ===========================================
# Request Gateway Service — Environment Variables
# ===========================================
# Copy this file to .env and fill in the values
# cp .env.example .env

# --- Biruni ERP Integration ---
BIRUNI_BASE_URL=http://localhost:8080/smartup24
BIRUNI_USERNAME=your_username
BIRUNI_PASSWORD=your_password
BIRUNI_PULL_URI=/b/biruni/bmb/requests$pull
BIRUNI_SAVE_URI=/b/biruni/bmb/requests$save
BIRUNI_CONNECTION_TIMEOUT=60

# --- OAuth2 Provider (Smartup) ---
OAUTH2_SMARTUP_TOKEN_URL=http://localhost:8080/b6/security/oauth/token
OAUTH2_SMARTUP_CLIENT_ID=your_client_id
OAUTH2_SMARTUP_CLIENT_SECRET=your_client_secret
OAUTH2_SMARTUP_SCOPE=read+write

# --- Kafka ---
KAFKA_BOOTSTRAP_SERVERS=localhost:19092

# --- Redis ---
REDIS_HOST=localhost
REDIS_PORT=6379

# --- Grafana ---
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=changeme
```

### Задача 0.4: Обновить .gitignore

**Файл:** `.gitignore` — добавить в конец:

```gitignore
### Secrets & Environment ###
.env
.env.local
.env.*.local
application-local.yml
application-local.properties
application-prod.yml
application-prod.properties

### Logs ###
*.log
logs/
```

### Задача 0.5: Очистка git-истории от секретов

> **ВНИМАНИЕ:** Эта операция перезаписывает историю. Все участники проекта должны заново клонировать репозиторий после выполнения.

**Вариант A — BFG Repo-Cleaner (рекомендуется):**

```bash
# 1. Установить BFG (https://rtyley.github.io/bfg-repo-cleaner/)
# 2. Создать файл passwords.txt со всеми секретами (по одному на строку):
echo "greenwhite" > passwords.txt
echo "ASLKDGHAJGF1341ASGHFAGFHj" >> passwords.txt
echo "12344432421" >> passwords.txt
echo "admin123" >> passwords.txt
echo "admin@head" >> passwords.txt

# 3. Запустить очистку:
bfg --replace-text passwords.txt

# 4. Очистить reflog и собрать мусор:
git reflog expire --expire=now --all
git gc --prune=now --aggressive

# 5. Force push (только после согласования с командой!):
git push --force --all

# 6. Удалить файл passwords.txt
rm passwords.txt
```

**Вариант B — Начать с чистой историей (проще):**

```bash
# Если git-история не представляет ценности:
# 1. Удалить .git
rm -rf .git

# 2. Инициализировать чистый репозиторий
git init
git add .
git commit -m "Initial commit: Request Gateway Service v1.0.0"
```

---

## Фаза 1: Юридические и организационные файлы

### Задача 1.1: Создать LICENSE

**Создать файл:** `LICENSE` (в корне проекта)

Рекомендуется **Apache License 2.0** — стандарт для enterprise Java-проектов. Обеспечивает защиту от патентных претензий и совместимость с большинством других лицензий.

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
   ...
```

Полный текст: https://www.apache.org/licenses/LICENSE-2.0.txt

Скопировать содержимое по ссылке в файл `LICENSE`. В начало добавить:

```
Copyright 2026 Green White Solutions

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
```

**Альтернативы:**
- MIT — если предпочитаете максимальную простоту
- GPL 3.0 — если хотите copyleft (все форки тоже open-source)

### Задача 1.2: Создать CONTRIBUTING.md

**Создать файл:** `CONTRIBUTING.md`

```markdown
# Contributing to Request Gateway Service

Thank you for your interest in contributing!

## How to Contribute

### Reporting Bugs

1. Check existing [Issues](../../issues) to avoid duplicates
2. Create a new issue with:
   - Clear title describing the problem
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment details (Java version, OS, etc.)

### Suggesting Features

1. Open an issue with the `enhancement` label
2. Describe the use case and expected behavior
3. Discuss before implementing

### Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Follow the code style (see below)
4. Write tests for new functionality
5. Ensure all tests pass: `mvn verify`
6. Commit with clear messages
7. Push and create a Pull Request

## Development Setup

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### Running Locally

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Copy environment config
cp .env.example .env
# Edit .env with your values

# 3. Run application
mvn spring-boot:run
```

### Running Tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker)
mvn verify -P integration-tests
```

## Code Style

- Java 21 features encouraged (records, pattern matching, etc.)
- Use Lombok for boilerplate reduction
- Follow existing package structure
- All public methods must have Javadoc
- Logging: use SLF4J via `@Slf4j` annotation
- Log levels: ERROR (failures), WARN (retries/degradation), INFO (events), DEBUG (tracing)

## Commit Messages

Format: `type: short description`

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`

Examples:
- `feat: add retry backoff strategy`
- `fix: resolve Redis lock race condition`
- `docs: update configuration reference`
- `test: add RequestConsumer unit tests`
```

### Задача 1.3: Создать SECURITY.md

**Создать файл:** `SECURITY.md`

```markdown
# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability, please report it responsibly:

1. **DO NOT** open a public issue
2. Email: [security@your-domain.com]
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
4. You will receive a response within 48 hours

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.x     | :white_check_mark: |

## Security Best Practices

When deploying this service:

- Never commit credentials to version control
- Use environment variables for all secrets
- Enable TLS for Kafka and Redis connections in production
- Restrict network access to management ports (8091)
- Rotate OAuth2 client secrets regularly
- Monitor the Dead Letter Queue for anomalies
```

### Задача 1.4: Создать CHANGELOG.md

**Создать файл:** `CHANGELOG.md`

```markdown
# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-XX-XX

### Added
- Kafka-based asynchronous request processing pipeline
- Redis-backed request state management with distributed locking
- Dynamic concurrency scaling based on Kafka consumer lag
- Circuit Breaker pattern (Resilience4j) for external API calls
- Retry logic with configurable attempts and retryable HTTP status codes
- Dead Letter Queue (DLQ) for permanently failed requests
- OAuth2 client credentials flow with Redis token caching
- Prometheus metrics for all pipeline stages (E1-E5)
- Grafana + Prometheus monitoring stack via Docker Compose
- Graceful shutdown support
- Health check endpoints via Spring Actuator
- Oracle Biruni ERP integration (pull requests, save responses)
```

---

## Фаза 2: Документация (README)

### Задача 2.1: Создать README.md

**Создать файл:** `README.md`

Ниже — полный шаблон. Адаптируйте под свои нужды:

```markdown
# Request Gateway Service

Asynchronous API gateway that bridges Oracle Biruni ERP with external APIs through a Kafka-based pipeline with built-in resilience, dynamic scaling, and comprehensive monitoring.

## Features

- **Async Processing** — Kafka-based pipeline decouples request ingestion from HTTP execution
- **Circuit Breaker** — Resilience4j protects against cascading failures from external APIs
- **Dynamic Concurrency** — Auto-scales consumer threads (3–15) based on real-time Kafka lag
- **Retry & DLQ** — Configurable retry with exponential backoff; Dead Letter Queue for failed requests
- **Distributed Locking** — Redis-based idempotency prevents duplicate processing
- **OAuth2** — Client credentials flow with automatic token refresh and Redis caching
- **Observability** — Prometheus metrics across 5 pipeline stages + Grafana dashboards

## Architecture

```
┌──────────────┐    ┌───────────────────┐    ┌──────────────┐
│ Oracle/Biruni│───▶│  Kafka Pipeline   │───▶│ External API │
│   (Source)   │ E1 │                   │ E4 │   (Target)   │
└──────────────┘    │  bmb.request.new  │    └──────┬───────┘
                    │  bmb.request.resp │           │
                    │  bmb.request.dlq  │           │
                    └────────┬──────────┘           │
                             │ E5                   │
                    ┌────────▼──────────┐           │
                    │  Oracle/Biruni    │◀──────────┘
                    │  (Save Response)  │    Response
                    └───────────────────┘
```

**Pipeline Stages:**

| Stage | Component | Description |
|-------|-----------|-------------|
| E1 | RequestPuller | Polls Oracle for new requests (scheduled, configurable batch) |
| E2 | RequestProducer | Publishes to Kafka with retry |
| E3 | RequestConsumer | Consumes from Kafka with idempotency check and distributed lock |
| E4 | HttpRequestService | Sends HTTP request with Circuit Breaker and OAuth2 |
| E5 | ResponseConsumer | Saves response back to Oracle |
| DLQ | Dead Letter Queue | Captures permanently failed requests |

**Request State Machine:**

```
NEW ──▶ PROCESSING ──▶ SENT ──▶ COMPLETED ──▶ DONE
                         │
                         ▼
                       FAILED ──▶ DLQ
```

## Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.0 |
| Message Queue | Apache Kafka (Redpanda) | 24.1.1 |
| Cache/State | Redis | 7.2 |
| Resilience | Resilience4j | 2.2.0 |
| Metrics | Micrometer + Prometheus | - |
| Dashboards | Grafana | 10.4.1 |
| Build | Maven | 3.9+ |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts: Redpanda (Kafka), Redis, Prometheus, Grafana, and management UIs.

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env with your Biruni credentials and OAuth2 settings
```

### 3. Run Application

```bash
mvn spring-boot:run
```

### 4. Verify

| Service | URL |
|---------|-----|
| Application | http://localhost:8090 |
| Actuator Health | http://localhost:8091/actuator/health |
| Kafka Console | http://localhost:8082 |
| Redis Commander | http://localhost:8085 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

## Configuration Reference

All settings are configurable via `application.yml` or environment variables.

### Gateway Core

| Property | Env Variable | Default | Description |
|----------|-------------|---------|-------------|
| `gateway.polling.enabled` | - | `true` | Enable Oracle polling |
| `gateway.polling.interval-ms` | - | `5000` | Polling interval (ms) |
| `gateway.polling.batch-size` | - | `100` | Requests per poll |

### HTTP Client

| Property | Default | Description |
|----------|---------|-------------|
| `gateway.http.connect-timeout-ms` | `10000` | Connection timeout |
| `gateway.http.read-timeout-ms` | `30000` | Read timeout |
| `gateway.http.write-timeout-ms` | `30000` | Write timeout |

### Retry Policy

| Property | Default | Description |
|----------|---------|-------------|
| `gateway.retry.max-attempts` | `3` | Max retry attempts |
| `gateway.retry.interval-ms` | `3000` | Delay between retries |
| `gateway.retry.retryable-statuses` | `408,429,500,502,503,504` | HTTP codes to retry |

### Dynamic Concurrency

| Property | Default | Description |
|----------|---------|-------------|
| `gateway.concurrency.min-concurrency` | `3` | Minimum consumer threads |
| `gateway.concurrency.max-concurrency` | `15` | Maximum consumer threads |
| `gateway.concurrency.scale-up-threshold` | `50` | Lag to trigger scale-up |
| `gateway.concurrency.scale-down-threshold` | `10` | Lag to trigger scale-down |
| `gateway.concurrency.scale-step` | `2` | Threads per scaling step |
| `gateway.concurrency.scale-cooldown-ms` | `30000` | Cooldown between scaling |

### Circuit Breaker

| Property | Default | Description |
|----------|---------|-------------|
| `resilience4j.circuitbreaker.instances.externalApi.failure-rate-threshold` | `50` | Failure % to open circuit |
| `resilience4j.circuitbreaker.instances.externalApi.slow-call-rate-threshold` | `80` | Slow call % to open circuit |
| `resilience4j.circuitbreaker.instances.externalApi.slow-call-duration-threshold` | `10s` | Slow call threshold |
| `resilience4j.circuitbreaker.instances.externalApi.wait-duration-in-open-state` | `30s` | Time before half-open |
| `resilience4j.circuitbreaker.instances.externalApi.sliding-window-size` | `10` | Window size (calls) |

### External Integrations

| Property | Env Variable | Description |
|----------|-------------|-------------|
| `gateway.biruni.base-url` | `BIRUNI_BASE_URL` | Oracle Biruni server URL |
| `gateway.biruni.username` | `BIRUNI_USERNAME` | Biruni auth username |
| `gateway.biruni.password` | `BIRUNI_PASSWORD` | Biruni auth password |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address |
| `spring.data.redis.host` | `REDIS_HOST` | Redis server host |
| `spring.data.redis.port` | `REDIS_PORT` | Redis server port |

### Kafka Topics

| Topic | Partitions | Purpose |
|-------|-----------|---------|
| `bmb.request.new` | 10 | Incoming requests |
| `bmb.request.response` | 10 | Processed responses |
| `bmb.request.dlq` | 3 | Failed requests (Dead Letter Queue) |

## Metrics

All metrics are exposed at `/actuator/prometheus` (port 8091).

| Metric | Type | Stage | Description |
|--------|------|-------|-------------|
| `gateway.oracle.pull.timer` | Timer | E1 | Oracle poll duration |
| `gateway.oracle.pull.success` | Counter | E1 | Successful polls |
| `gateway.oracle.pull.error` | Counter | E1 | Failed polls |
| `gateway.kafka.produce.timer` | Timer | E2 | Kafka send duration |
| `gateway.kafka.produce.success` | Counter | E2 | Successful sends |
| `gateway.consumer.received` | Counter | E3 | Messages consumed |
| `gateway.consumer.skipped.duplicate` | Counter | E3 | Idempotent skips |
| `gateway.consumer.lock.failed` | Counter | E3 | Lock acquisition failures |
| `gateway.http.request.timer` | Timer | E4 | HTTP request duration |
| `gateway.http.success` | Counter | E4 | 2xx responses |
| `gateway.http.error.4xx` | Counter | E4 | 4xx responses |
| `gateway.http.error.5xx` | Counter | E4 | 5xx responses |
| `gateway.http.timeout` | Counter | E4 | Timeouts |
| `gateway.http.circuit_breaker.open` | Counter | E4 | Circuit breaker rejections |
| `gateway.oracle.save.timer` | Timer | E5 | Oracle save duration |
| `gateway.dlq.sent` | Counter | DLQ | Messages sent to DLQ |
| `gateway.request.e2e.timer` | Timer | E2E | End-to-end request duration |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## Security

See [SECURITY.md](SECURITY.md)

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.
```

---

## Фаза 3: Очистка кода

### Задача 3.1: Удалить или изолировать TestController

**Файл:** `src/main/java/uz/greenwhite/gateway/controller/TestController.java`

**Вариант A (рекомендуется) — Удалить полностью:**

```bash
# Файл содержит комментарий "DELETE THIS IN PRODUCTION!" на строке 18
# Открывает незащищённые эндпоинты /api/test/* для отправки произвольных запросов
git rm src/main/java/uz/greenwhite/gateway/controller/TestController.java
```

**Вариант B — Изолировать через Spring Profile:**

Если контроллер нужен для разработки, добавить аннотацию:

```java
// Строка 22 — добавить перед @RestController:
@Profile("dev")
@RestController
@RequestMapping("/api/test")
public class TestController {
```

Импорт: `import org.springframework.context.annotation.Profile;`

### Задача 3.2: Перевести комментарии на английский

Найти и перевести все комментарии на узбекском/русском языке:

| Файл | Строка | Текущий текст | Замена |
|------|--------|---------------|--------|
| `application.yml` | 27 | `# Oracle (hozircha disable, keyinroq enable qilamiz)` | `# Oracle (disabled — enable when Oracle is available)` |
| `RequestConsumer.java` | 82 | `// 3. HTTP ishni alohida thread pool ga topshirish` | `// 3. Delegate HTTP work to dedicated thread pool` |
| `RequestConsumer.java` | 126 | `// Timeout yoki connection error?` | `// Timeout or connection error?` |
| `HttpRequestService.java` | 55 | `// 1. Circuit Breaker OPEN bo'lsa — darhol reject` | `// 1. If Circuit Breaker is OPEN — reject immediately` |
| `HttpRequestService.java` | 63 | `// 2. OAuth2 Authorization header qo'shish` | `// 2. Add OAuth2 Authorization header` |
| `HttpRequestService.java` | 104-105 | `OAuth2 token olish va headers'ga qo'shish / Eski tizim pattern` | `Get OAuth2 token and add to headers` |

**Как найти все такие места:**

```bash
# Поиск кириллицы и узбекских слов в Java-файлах:
# Через IDE — найти regex в *.java файлах: [а-яА-ЯёЁ]
# Также проверить слова: bo'lsa, qo'shish, olish, ishni, hozircha, keyinroq, uchun, alohida
```

### Задача 3.3: Убрать emoji из логов

**Файл:** `src/main/java/uz/greenwhite/gateway/http/HttpRequestService.java`

```java
// Строки 42-46 — убрать emoji символы:

// БЫЛО:
log.warn("⚡ Circuit Breaker state change: {}", event.getStateTransition())
log.warn("⚠ Circuit Breaker failure rate exceeded: {}%", event.getFailureRate())
log.warn("⚠ Circuit Breaker slow call rate exceeded: {}%", event.getSlowCallRate())

// ДОЛЖНО СТАТЬ:
log.warn("Circuit Breaker state change: {}", event.getStateTransition())
log.warn("Circuit Breaker failure rate exceeded: {}%", event.getFailureRate())
log.warn("Circuit Breaker slow call rate exceeded: {}%", event.getSlowCallRate())
```

### Задача 3.4: Вынести магические числа в конфигурацию

| Файл | Строка | Значение | Рекомендация |
|------|--------|----------|--------------|
| `Token.java` | 11 | `15 * 1000` (margin) | Оставить как есть — это внутренняя константа record-а |
| `ThreadPoolConfig.java` | ~34 | `200` (queue capacity) | Вынести в `ConcurrencyProperties`: `private int httpQueueCapacity = 200;` |
| `RequestStateService.java` | 25-26 | `24` (часов TTL), `300` (сек lock) | Вынести в новый `StateProperties` или оставить с Javadoc |

**Минимальный вариант** — добавить Javadoc к константам в `RequestStateService.java`:

```java
/** Request state TTL — states expire after this period to prevent Redis bloat */
private static final long STATE_TTL_HOURS = 24;

/** Distributed lock TTL — prevents stale locks from orphaned processes */
private static final long LOCK_TTL_SECONDS = 300;
```

---

## Фаза 4: Тестирование

> **Текущее состояние: 0 тестов, 0% coverage.**
> **Цель: минимум 60% coverage для ключевых классов.**

### Задача 4.1: Добавить тестовые зависимости в pom.xml

**Файл:** `pom.xml` — добавить в секцию `<dependencies>` после строки 103:

```xml
        <!-- Testing — Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Testing — Testcontainers -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Testing — WireMock (mock external HTTP APIs) -->
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock-standalone</artifactId>
            <version>3.5.4</version>
            <scope>test</scope>
        </dependency>
```

Добавить в `<dependencyManagement>` секцию (после строки 128):

```xml
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>1.19.7</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
```

### Задача 4.2: Создать тестовую конфигурацию

**Создать файл:** `src/test/resources/application-test.yml`

```yaml
spring:
  kafka:
    bootstrap-servers: ${spring.embedded.kafka.brokers:localhost:9092}
    consumer:
      auto-offset-reset: earliest
      group-id: test-group
  data:
    redis:
      host: localhost
      port: 6379
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

gateway:
  polling:
    enabled: false  # Disable Oracle polling in tests
  biruni:
    base-url: http://localhost:8080
    username: test
    password: test
  retry:
    max-attempts: 2
    interval-ms: 100
    retryable-statuses: 408,429,500,502,503,504
  concurrency:
    min-concurrency: 1
    max-concurrency: 3
    monitor-interval-ms: 1000
    scale-up-threshold: 50
    scale-down-threshold: 10
    scale-step: 1
    scale-cooldown-ms: 1000
    topic-partitions: 3

logging:
  level:
    uz.greenwhite.gateway: DEBUG
```

### Задача 4.3: Unit-тесты (приоритет 1 — ОБЯЗАТЕЛЬНО)

Создать следующую структуру:

```
src/test/java/uz/greenwhite/gateway/
├── config/
│   ├── RetryPropertiesTest.java
│   └── ConcurrencyPropertiesTest.java
├── model/
│   └── TokenTest.java
├── state/
│   └── RequestStateServiceTest.java
├── http/
│   └── HttpRequestServiceTest.java
├── kafka/
│   ├── consumer/
│   │   └── RequestConsumerTest.java
│   └── producer/
│       └── RequestProducerTest.java
└── GatewayApplicationTest.java
```

---

#### 4.3.1: `RetryPropertiesTest.java`

**Создать:** `src/test/java/uz/greenwhite/gateway/config/RetryPropertiesTest.java`

```java
package uz.greenwhite.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPropertiesTest {

    private RetryProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RetryProperties();
    }

    @Test
    void defaultValues_shouldBeCorrect() {
        assertThat(properties.getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getIntervalMs()).isEqualTo(3000);
        assertThat(properties.getRetryableStatuses()).isEqualTo("408,429,500,502,503,504");
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void isRetryable_shouldReturnTrue_forRetryableStatuses(int status) {
        assertThat(properties.isRetryable(status)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 400, 401, 403, 404, 405})
    void isRetryable_shouldReturnFalse_forNonRetryableStatuses(int status) {
        assertThat(properties.isRetryable(status)).isFalse();
    }

    @Test
    void getRetryableStatusSet_shouldParseAllStatuses() {
        assertThat(properties.getRetryableStatusSet())
                .containsExactlyInAnyOrder(408, 429, 500, 502, 503, 504);
    }

    @Test
    void customRetryableStatuses_shouldBeParsed() {
        properties.setRetryableStatuses("502,503");
        assertThat(properties.getRetryableStatusSet())
                .containsExactlyInAnyOrder(502, 503);
        assertThat(properties.isRetryable(500)).isFalse();
        assertThat(properties.isRetryable(502)).isTrue();
    }
}
```

---

#### 4.3.2: `ConcurrencyPropertiesTest.java`

**Создать:** `src/test/java/uz/greenwhite/gateway/config/ConcurrencyPropertiesTest.java`

```java
package uz.greenwhite.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyPropertiesTest {

    private ConcurrencyProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ConcurrencyProperties();
        // defaults: min=3, max=15, scaleUp=50, scaleDown=10
    }

    @Test
    void calculateDesiredConcurrency_belowScaleDown_shouldReturnMin() {
        assertThat(properties.calculateDesiredConcurrency(0)).isEqualTo(3);
        assertThat(properties.calculateDesiredConcurrency(5)).isEqualTo(3);
        assertThat(properties.calculateDesiredConcurrency(10)).isEqualTo(3);
    }

    @Test
    void calculateDesiredConcurrency_betweenThresholds_shouldReturnNoChange() {
        assertThat(properties.calculateDesiredConcurrency(25)).isEqualTo(-1);
        assertThat(properties.calculateDesiredConcurrency(49)).isEqualTo(-1);
    }

    @Test
    void calculateDesiredConcurrency_aboveScaleUp_shouldScaleUp() {
        int result = properties.calculateDesiredConcurrency(100);
        assertThat(result).isBetween(3, 15);
        assertThat(result).isGreaterThan(3);
    }

    @Test
    void calculateDesiredConcurrency_veryHighLag_shouldReturnMax() {
        int result = properties.calculateDesiredConcurrency(500);
        assertThat(result).isEqualTo(15);
    }

    @Test
    void calculateDesiredConcurrency_justAboveScaleUp_shouldReturnSlightlyAboveMin() {
        int result = properties.calculateDesiredConcurrency(51);
        assertThat(result).isGreaterThanOrEqualTo(3);
        assertThat(result).isLessThanOrEqualTo(15);
    }
}
```

---

#### 4.3.3: `TokenTest.java`

**Создать:** `src/test/java/uz/greenwhite/gateway/model/TokenTest.java`

```java
package uz.greenwhite.gateway.model;

import org.junit.jupiter.api.Test;
import uz.greenwhite.gateway.oauth2.model.Token;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTest {

    @Test
    void isExpired_shouldReturnFalse_forFreshToken() {
        Token token = new Token("access123", 60_000, "Bearer", "refresh123");
        assertThat(token.isExpired()).isFalse();
    }

    @Test
    void isExpired_shouldReturnTrue_forExpiredToken() {
        // Token created in the past (createdAt = 0) with 1 second expiry
        Token token = new Token("access123", 1_000, "Bearer", "refresh123", 0);
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void isExpired_shouldAccountForMargin() {
        // Token that expires in 10 seconds but margin is 15 seconds — should be "expired"
        Token token = new Token("access123", 10_000, "Bearer", "refresh123");
        // 10s < 15s margin, so it should be considered expired
        assertThat(token.isExpired()).isTrue();
    }

    @Test
    void getAuthorizationHeader_shouldReturnBearer_forBearerType() {
        Token token = new Token("abc123", 60_000, "Bearer", null);
        assertThat(token.getAuthorizationHeader()).isEqualTo("Bearer abc123");
    }

    @Test
    void getAuthorizationHeader_shouldReturnBearer_caseInsensitive() {
        Token token = new Token("abc123", 60_000, "bearer", null);
        assertThat(token.getAuthorizationHeader()).isEqualTo("Bearer abc123");
    }

    @Test
    void getAuthorizationHeader_shouldReturnCustomType_forNonBearer() {
        Token token = new Token("abc123", 60_000, "MAC", null);
        assertThat(token.getAuthorizationHeader()).isEqualTo("MAC abc123");
    }
}
```

---

#### 4.3.4: `RequestStateServiceTest.java`

**Создать:** `src/test/java/uz/greenwhite/gateway/state/RequestStateServiceTest.java`

```java
package uz.greenwhite.gateway.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import uz.greenwhite.gateway.model.RequestState;
import uz.greenwhite.gateway.model.enums.RequestStatus;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestStateServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RequestStateService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RequestStateService(redisTemplate);
    }

    @Test
    void createInitialState_shouldSaveWithProcessingStatus() {
        service.createInitialState("100:123");

        verify(valueOperations).set(
                eq("request:state:100:123"),
                argThat(obj -> obj instanceof RequestState state
                        && state.getStatus() == RequestStatus.PROCESSING
                        && state.getAttemptCount() == 0),
                eq(24L),
                eq(TimeUnit.HOURS)
        );
    }

    @Test
    void getState_shouldReturnState_whenExists() {
        RequestState mockState = RequestState.builder()
                .compositeId("100:123")
                .status(RequestStatus.PROCESSING)
                .build();
        when(valueOperations.get("request:state:100:123")).thenReturn(mockState);

        var result = service.getState("100:123");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(RequestStatus.PROCESSING);
    }

    @Test
    void getState_shouldReturnEmpty_whenNotExists() {
        when(valueOperations.get("request:state:100:999")).thenReturn(null);

        var result = service.getState("100:999");

        assertThat(result).isEmpty();
    }

    @Test
    void isCompleted_shouldReturnTrue_forDoneStatus() {
        RequestState state = RequestState.builder()
                .compositeId("100:123")
                .status(RequestStatus.DONE)
                .build();
        when(valueOperations.get("request:state:100:123")).thenReturn(state);

        assertThat(service.isCompleted("100:123")).isTrue();
    }

    @Test
    void isCompleted_shouldReturnTrue_forFailedStatus() {
        RequestState state = RequestState.builder()
                .compositeId("100:123")
                .status(RequestStatus.FAILED)
                .build();
        when(valueOperations.get("request:state:100:123")).thenReturn(state);

        assertThat(service.isCompleted("100:123")).isTrue();
    }

    @Test
    void isCompleted_shouldReturnFalse_forProcessingStatus() {
        RequestState state = RequestState.builder()
                .compositeId("100:123")
                .status(RequestStatus.PROCESSING)
                .build();
        when(valueOperations.get("request:state:100:123")).thenReturn(state);

        assertThat(service.isCompleted("100:123")).isFalse();
    }

    @Test
    void isCompleted_shouldReturnFalse_whenNoState() {
        when(valueOperations.get("request:state:100:999")).thenReturn(null);

        assertThat(service.isCompleted("100:999")).isFalse();
    }

    @Test
    void tryLock_shouldReturnTrue_whenAcquired() {
        when(valueOperations.setIfAbsent(eq("request:lock:100:123"), any(), eq(Duration.ofSeconds(300))))
                .thenReturn(true);

        assertThat(service.tryLock("100:123")).isTrue();
    }

    @Test
    void tryLock_shouldReturnFalse_whenAlreadyLocked() {
        when(valueOperations.setIfAbsent(eq("request:lock:100:123"), any(), eq(Duration.ofSeconds(300))))
                .thenReturn(false);

        assertThat(service.tryLock("100:123")).isFalse();
    }

    @Test
    void releaseLock_shouldDeleteLockKey() {
        service.releaseLock("100:123");

        verify(redisTemplate).delete("request:lock:100:123");
    }

    @Test
    void incrementAttempt_shouldReturnIncrementedCount() {
        RequestState state = RequestState.builder()
                .compositeId("100:123")
                .status(RequestStatus.PROCESSING)
                .attemptCount(1)
                .build();
        when(valueOperations.get("request:state:100:123")).thenReturn(state);

        int result = service.incrementAttempt("100:123");

        assertThat(result).isEqualTo(2);
    }

    @Test
    void incrementAttempt_shouldReturnZero_whenNoState() {
        when(valueOperations.get("request:state:100:999")).thenReturn(null);

        assertThat(service.incrementAttempt("100:999")).isEqualTo(0);
    }
}
```

---

#### 4.3.5: `HttpRequestServiceTest.java`

**Создать:** `src/test/java/uz/greenwhite/gateway/http/HttpRequestServiceTest.java`

```java
package uz.greenwhite.gateway.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.model.kafka.ResponseMessage;
import uz.greenwhite.gateway.oauth2.OAuth2ProviderService;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class HttpRequestServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private OAuth2ProviderService oAuth2ProviderService;

    private HttpRequestService service;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slidingWindowSize(5)
                        .minimumNumberOfCalls(3)
                        .build()
        );

        service = new HttpRequestService(webClient, circuitBreakerRegistry, oAuth2ProviderService);
        service.init();
    }

    @Test
    void sendRequest_whenCircuitBreakerOpen_shouldReturn503() {
        // Force circuit breaker to OPEN state
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("externalApi");
        cb.transitionToOpenState();

        RequestMessage request = RequestMessage.builder()
                .companyId(100L)
                .requestId(1L)
                .baseUrl("http://example.com")
                .uri("/api")
                .method("GET")
                .build();

        ResponseMessage response = service.sendRequest(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getHttpStatus()).isEqualTo(503);
        assertThat(response.getErrorMessage()).contains("Circuit breaker is OPEN");
    }
}
```

---

#### 4.3.6: `RequestConsumerTest.java`

**Создать:** `src/test/java/uz/greenwhite/gateway/kafka/consumer/RequestConsumerTest.java`

```java
package uz.greenwhite.gateway.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uz.greenwhite.gateway.config.RetryProperties;
import uz.greenwhite.gateway.http.HttpRequestService;
import uz.greenwhite.gateway.kafka.producer.RequestProducer;
import uz.greenwhite.gateway.metrics.GatewayMetrics;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.state.RequestStateService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestConsumerTest {

    @Mock private HttpRequestService httpRequestService;
    @Mock private RequestStateService requestStateService;
    @Mock private RequestProducer requestProducer;
    @Mock private RetryProperties retryProperties;
    @Mock private GatewayMetrics metrics;
    @Mock private Acknowledgment acknowledgment;

    private RequestConsumer consumer;
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.initialize();

        // Stub metric counters to avoid NPE
        lenient().when(metrics.getConsumerReceived())
                .thenReturn(new io.micrometer.core.instrument.Counter() {
                    public void increment(double amount) {}
                    public double count() { return 0; }
                    public Id getId() { return null; }
                });
        lenient().when(metrics.getConsumerSkippedDuplicate())
                .thenReturn(metrics.getConsumerReceived());
        lenient().when(metrics.getConsumerLockFailed())
                .thenReturn(metrics.getConsumerReceived());

        consumer = new RequestConsumer(
                httpRequestService, requestStateService, requestProducer,
                retryProperties, executor, metrics
        );
    }

    @Test
    void consumeRequest_shouldSkipDuplicate_whenAlreadyCompleted() {
        when(requestStateService.isCompleted("100:1")).thenReturn(true);

        RequestMessage msg = RequestMessage.builder()
                .companyId(100L).requestId(1L).build();
        ConsumerRecord<String, RequestMessage> record =
                new ConsumerRecord<>("test-topic", 0, 0, "100:1", msg);

        consumer.consumeRequest(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(requestStateService, never()).tryLock(any());
    }

    @Test
    void consumeRequest_shouldSkip_whenLockNotAcquired() {
        when(requestStateService.isCompleted("100:2")).thenReturn(false);
        when(requestStateService.tryLock("100:2")).thenReturn(false);

        RequestMessage msg = RequestMessage.builder()
                .companyId(100L).requestId(2L).build();
        ConsumerRecord<String, RequestMessage> record =
                new ConsumerRecord<>("test-topic", 0, 0, "100:2", msg);

        consumer.consumeRequest(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(httpRequestService, never()).sendRequest(any());
    }
}
```

---

### Задача 4.4: Integration-тесты (приоритет 2 — ЖЕЛАТЕЛЬНО)

Ниже — описание подхода. Конкретная реализация зависит от имеющейся инфраструктуры.

**Создать:** `src/test/java/uz/greenwhite/gateway/integration/KafkaPipelineIntegrationTest.java`

```java
package uz.greenwhite.gateway.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uz.greenwhite.gateway.model.kafka.RequestMessage;
import uz.greenwhite.gateway.state.RequestStateService;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full pipeline integration test:
 * Kafka message → Consumer → HTTP (mocked) → Response topic
 *
 * Requirements:
 * - Docker must be running for Testcontainers
 * - Add org.awaitility:awaitility dependency for async assertions
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EmbeddedKafka(
    partitions = 3,
    topics = {"bmb.request.new", "bmb.request.response", "bmb.request.dlq"}
)
class KafkaPipelineIntegrationTest {

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine")
                    .withExposedPorts(6379);

    @Autowired
    private KafkaTemplate<String, RequestMessage> kafkaTemplate;

    @Autowired
    private RequestStateService stateService;

    @Test
    void fullPipeline_shouldProcessRequestEndToEnd() {
        RequestMessage request = RequestMessage.builder()
                .companyId(100L)
                .requestId(999L)
                .baseUrl("http://httpbin.org")
                .uri("/post")
                .method("POST")
                .body("{\"test\": true}")
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send("bmb.request.new", request.getCompositeId(), request);

        // Wait for processing to complete
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var state = stateService.getState("100:999");
            assertThat(state).isPresent();
            // State should be either COMPLETED or FAILED (depending on httpbin availability)
        });
    }
}
```

**Дополнительная зависимость для async-тестов:**

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

---

## Фаза 5: CI/CD и инструменты качества

### Задача 5.1: GitHub Actions CI Pipeline

**Создать:** `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build and Test
        run: mvn clean verify --batch-mode

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: target/surefire-reports/

      - name: Upload Coverage Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: coverage-report
          path: target/site/jacoco/
```

### Задача 5.2: Добавить JaCoCo для отчётов о покрытии

**Файл:** `pom.xml` — добавить в секцию `<plugins>` (после строки 146):

```xml
            <!-- Code Coverage -->
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.12</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

После этого отчёт о покрытии будет генерироваться при `mvn verify` в `target/site/jacoco/index.html`.

### Задача 5.3: Добавить .editorconfig

**Создать файл:** `.editorconfig`

```ini
root = true

[*]
charset = utf-8
end_of_line = lf
indent_style = space
indent_size = 4
trim_trailing_whitespace = true
insert_final_newline = true

[*.md]
trim_trailing_whitespace = false

[*.yml]
indent_size = 2

[*.xml]
indent_size = 4

[Makefile]
indent_style = tab
```

---

## Фаза 6: Финальная полировка

### Задача 6.1: Добавить badges в README.md

В самое начало `README.md` (перед заголовком) добавить:

```markdown
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-green)
![License](https://img.shields.io/badge/License-Apache%202.0-blue)
```

Если будет настроен GitHub Actions, добавить:

```markdown
![CI](https://github.com/YOUR_ORG/request-gateway-service/actions/workflows/ci.yml/badge.svg)
```

### Задача 6.2: GitHub Issue & PR Templates

**Создать:** `.github/ISSUE_TEMPLATE/bug_report.md`

```markdown
---
name: Bug report
about: Create a report to help us improve
title: '[BUG] '
labels: bug
---

**Describe the bug**
A clear and concise description.

**To Reproduce**
Steps to reproduce:
1. ...

**Expected behavior**
A clear and concise description.

**Environment:**
 - Java version:
 - OS:
 - Service version:

**Logs**
```
Paste relevant log output here
```
```

**Создать:** `.github/PULL_REQUEST_TEMPLATE.md`

```markdown
## Summary
Brief description of changes.

## Changes
- ...

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing performed

## Checklist
- [ ] No hardcoded credentials
- [ ] Documentation updated if needed
- [ ] Follows existing code style
```

### Задача 6.3: Экспорт Grafana-дашбордов

Если в Grafana уже настроены дашборды:

```bash
# Экспортировать дашборд в JSON:
curl -s http://admin:admin123@localhost:3000/api/dashboards/db/your-dashboard \
  | jq '.dashboard' > infrastructure/grafana/dashboards/gateway-dashboard.json
```

Сохранить в `infrastructure/grafana/dashboards/` и добавить в docker-compose provisioning.

---

## Приложения

### Приложение A: Полный чеклист перед публикацией

```
ФАЗА 0 — Безопасность (БЛОКЕР):
[ ] Ротированы все скомпрометированные пароли/секреты на серверах
[ ] application.yml: credentials заменены на ${ENV_VAR}
[ ] docker-compose.yml: Grafana пароль вынесен в env
[ ] Создан .env.example
[ ] Обновлён .gitignore (добавлены .env, application-local.yml)
[ ] Git-история очищена от секретов (BFG или новый init)
[ ] Проведена финальная проверка: grep -r "greenwhite" --include="*.yml" --include="*.java"
[ ] Проведена финальная проверка: grep -r "ASLKDGHAJGF" --include="*.yml" --include="*.java"

ФАЗА 1 — Юридические файлы:
[ ] Создан LICENSE (Apache 2.0)
[ ] Создан CONTRIBUTING.md
[ ] Создан SECURITY.md
[ ] Создан CHANGELOG.md

ФАЗА 2 — Документация:
[ ] Создан README.md с архитектурой, Quick Start, Configuration Reference
[ ] README содержит таблицу всех environment variables
[ ] README содержит таблицу метрик

ФАЗА 3 — Код:
[ ] TestController удалён или помечен @Profile("dev")
[ ] Все комментарии переведены на английский
[ ] Emoji убраны из логов в HttpRequestService
[ ] Магические числа задокументированы или вынесены в конфиг

ФАЗА 4 — Тесты:
[ ] Добавлены тестовые зависимости в pom.xml
[ ] Создан application-test.yml
[ ] RetryPropertiesTest — написан, проходит
[ ] ConcurrencyPropertiesTest — написан, проходит
[ ] TokenTest — написан, проходит
[ ] RequestStateServiceTest — написан, проходит
[ ] HttpRequestServiceTest — написан, проходит
[ ] RequestConsumerTest — написан, проходит
[ ] mvn test — все тесты проходят

ФАЗА 5 — CI/CD:
[ ] .github/workflows/ci.yml создан
[ ] JaCoCo plugin добавлен в pom.xml
[ ] .editorconfig создан

ФАЗА 6 — Полировка:
[ ] Badges добавлены в README
[ ] GitHub Issue/PR templates созданы
[ ] Grafana дашборды экспортированы (если есть)
```

### Приложение B: Структура файлов после выполнения всех задач

```
request-gateway-service/
├── .editorconfig                          [NEW]
├── .env.example                           [NEW]
├── .gitignore                             [MODIFIED]
├── .github/
│   ├── workflows/
│   │   └── ci.yml                         [NEW]
│   ├── ISSUE_TEMPLATE/
│   │   └── bug_report.md                  [NEW]
│   └── PULL_REQUEST_TEMPLATE.md           [NEW]
├── CHANGELOG.md                           [NEW]
├── CONTRIBUTING.md                        [NEW]
├── LICENSE                                [NEW]
├── README.md                              [NEW]
├── SECURITY.md                            [NEW]
├── docker-compose.yml                     [MODIFIED — env vars]
├── infrastructure/
│   ├── grafana/
│   │   └── dashboards/                    [NEW — optional]
│   └── prometheus/
│       └── prometheus.yml
├── pom.xml                                [MODIFIED — test deps, jacoco]
└── src/
    ├── main/
    │   ├── java/uz/greenwhite/gateway/
    │   │   ├── controller/
    │   │   │   └── TestController.java    [DELETED or @Profile("dev")]
    │   │   ├── http/
    │   │   │   └── HttpRequestService.java [MODIFIED — comments, emoji]
    │   │   └── kafka/consumer/
    │   │       └── RequestConsumer.java    [MODIFIED — comments]
    │   └── resources/
    │       └── application.yml            [MODIFIED — env vars]
    └── test/
        ├── java/uz/greenwhite/gateway/   [NEW — entire directory]
        │   ├── GatewayApplicationTest.java
        │   ├── config/
        │   │   ├── RetryPropertiesTest.java
        │   │   └── ConcurrencyPropertiesTest.java
        │   ├── http/
        │   │   └── HttpRequestServiceTest.java
        │   ├── kafka/consumer/
        │   │   └── RequestConsumerTest.java
        │   ├── model/
        │   │   └── TokenTest.java
        │   └── state/
        │       └── RequestStateServiceTest.java
        └── resources/
            └── application-test.yml       [NEW]
```

### Приложение C: Полезные команды для проверки

```bash
# Проверить что секреты удалены из кода:
grep -rn "greenwhite\|ASLKDGHAJGF\|12344432421\|admin123\|admin@head" \
  --include="*.java" --include="*.yml" --include="*.yaml" --include="*.properties"

# Проверить что нет узбекских/русских комментариев:
grep -rn "[а-яА-ЯёЁ]" --include="*.java" src/

# Запустить тесты:
mvn clean test

# Запустить тесты с отчётом о покрытии:
mvn clean verify
# Открыть target/site/jacoco/index.html

# Проверить что проект собирается:
mvn clean package -DskipTests

# Проверить .gitignore работает:
git status
```

---

**Ответственные и сроки — определите самостоятельно на основе приоритетов:**

| Фаза | Приоритет | Примерный объём работы |
|------|-----------|----------------------|
| Фаза 0: Безопасность | БЛОКЕР | 2–4 часа |
| Фаза 1: Юридические файлы | БЛОКЕР | 1–2 часа |
| Фаза 2: README | ВЫСОКИЙ | 2–3 часа |
| Фаза 3: Очистка кода | ВЫСОКИЙ | 1–2 часа |
| Фаза 4: Тесты | ВЫСОКИЙ | 1–2 дня |
| Фаза 5: CI/CD | СРЕДНИЙ | 2–4 часа |
| Фаза 6: Полировка | НИЗКИЙ | 2–3 часа |
