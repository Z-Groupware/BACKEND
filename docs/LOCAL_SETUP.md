# 로컬 개발 환경 세팅

클론 후 **한 번만** 하면 된다. 순서대로 하면 애플리케이션이 뜬다.

> 이 문서가 없어서 실제로 막힌 사례가 있다(#44). 순서 중 하나라도 빠지면
> **원인을 가리키지 않는 에러**가 난다 — 증상별 원인은 맨 아래 표에 있다.

---

## 필요한 것

- JDK 17
- MySQL 8 (로컬 실행)
- Docker Desktop (로컬 Redis용)

---

## 1. MySQL 계정·데이터베이스 만들기

MySQL을 새로 설치하면 `root`만 있다. 애플리케이션이 쓰는 계정을 만든다:

```sql
CREATE USER IF NOT EXISTS 'module06'@'localhost' IDENTIFIED BY '본인이_정할_비밀번호';
CREATE DATABASE IF NOT EXISTS module06 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON module06.* TO 'module06'@'localhost';
FLUSH PRIVILEGES;
```

> 테이블은 만들지 않는다. **스키마의 주인은 Flyway**이고, 애플리케이션이 뜰 때 마이그레이션이 적용된다.
> 상세: [DB_MIGRATION_RULES.md](DB_MIGRATION_RULES.md)

## 2. Redis 비밀번호 정하기

로컬 Redis의 비밀번호는 **어딘가에 이미 있는 값이 아니다.** 본인이 정한 값이 컨테이너의 비밀번호가 된다.

```bash
openssl rand -hex 32
```

이 값을 **두 파일에 같이** 넣는다. 하나만 넣으면 안 된다.

| 파일 | 읽는 주체 | 역할 |
|---|---|---|
| `.env` | docker compose | 이 값으로 Redis를 **띄운다** (`--requirepass`) |
| `application-secret.yml` | Spring | 이 값으로 Redis에 **접속한다** (AUTH) |

## 3. 설정 파일 두 개 만들기

```bash
cp .env.example .env
cp application-secret.yml.example application-secret.yml
```

- `.env` → `REDIS_PASSWORD=` 에 2번에서 만든 값
- `application-secret.yml` → `DB_PASSWORD`에 1번 비밀번호, `REDIS_PASSWORD`에 **2번과 같은 값**

둘 다 **프로젝트 루트**에 둔다. `src/main/resources` 아래에 두면 읽히지 않고, `Dockerfile`의 `COPY src` 때문에 **이미지에 비밀값이 실려 나간다.**

> 두 파일 모두 `.gitignore` 처리돼 있다. 커밋되지 않는다.

## 4. Redis 띄우기

Docker Desktop을 먼저 실행한 뒤:

```bash
docker compose up -d redis
```

확인 — `healthy`가 나와야 한다:

```bash
docker ps --filter name=z-redis-local --format "{{.Names}} · {{.Status}}"
```

## 5. 애플리케이션 실행

IntelliJ에서 `BackendApplication`을 실행하거나:

```bash
./gradlew bootRun
```

성공하면 이렇게 보인다:

```
Migrating schema `module06` to version "1 - init schema"
...
Successfully applied N migrations to schema `module06`
Tomcat started on port 8080 (http)
Started BackendApplication in N seconds
```

확인:

```bash
curl http://localhost:8080/actuator/health     # {"status":"UP"}
```

---

## 증상별 원인

`spring.config.import`가 `optional:`이라 **설정 파일이 없거나 키가 빠져도 경고가 없다.** 그래서 실패가 엉뚱한 곳에서 터진다. 아래 표로 역추적한다.

| 증상 | 원인 | 조치 |
|---|---|---|
| `Could not resolve placeholder 'DB_HOST'` | `application-secret.yml`이 없거나 루트에 없음 | 3번. `src/main/resources`가 아니라 **루트** |
| `UnknownHostException (${DB_HOST})` | 같은 원인 — 치환 안 된 문자열이 그대로 접속 시도 | 3번 |
| `Could not resolve placeholder 'REDIS_PASSWORD'` | secret 파일에 그 키가 없음(기본값 없는 키) | 3번 |
| `Access denied for user 'module06'@'localhost'` | MySQL 계정이 없거나 `DB_PASSWORD`가 틀림 | 1번 |
| `Communications link failure` | MySQL이 안 떠 있음 / `DB_HOST` 오타 | MySQL 서비스 확인 |
| `NOAUTH Authentication required` | Redis엔 비밀번호가 있는데 Spring이 안 보냄 | `application-secret.yml`의 `REDIS_PASSWORD` |
| `AUTH ... without any password configured` | Spring은 보내는데 Redis엔 비밀번호가 없음 | `.env`가 비어 있다 → 2·3번 |
| `WRONGPASS invalid username-password pair` | 두 파일의 값이 다름 | 같은 값으로 맞춘다 |
| `Schema validation: wrong column type ...` | 엔티티와 마이그레이션 불일치 | **코드 버그다.** 이슈로 올릴 것(#44 참고) |
| `Port 8080 was already in use` | 이전 인스턴스가 살아 있음 | 그 프로세스 종료 |

> 마지막 항목 주의: `ddl-auto: validate`라서 엔티티와 실제 스키마가 다르면 부팅이 막힌다. **내 설정 문제가 아니라 코드 문제**다.
> 테스트는 H2 + `create-drop`이라 이 불일치를 잡지 못한다(스키마를 엔티티에서 생성하므로) — 항상 로컬에서 먼저 발견된다.

---

## 참고

| 문서 | 내용 |
|---|---|
| [DB_MIGRATION_RULES.md](DB_MIGRATION_RULES.md) | 담당자별 버전 영역 · 머지된 마이그레이션 수정 금지 |
| [../review-loop/SETUP.md](../review-loop/SETUP.md) | AI 코드 리뷰 루프 세팅(push 게이트) |
| `compose.yml` | 로컬 Redis |
| `infra/docker-compose.yml` · `infra/deploy.sh` | 운영 배포(SSM에서 비밀값 주입) |
