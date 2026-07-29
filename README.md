# Petopia

반려동물 예약 서비스 백엔드. Spring Boot 4.1 + MyBatis + Flyway + MySQL + Redis.

이 문서는 **클론 직후 로컬에서 서버를 띄우기까지**를 다룬다.

---

## 1. 사전 준비

| 항목             | 버전        | 확인                     |
|----------------|-----------|------------------------|
| JDK            | 21        | `java -version`        |
| Docker Desktop | 최신        | `docker -v`            |
| Docker Compose | v2 (플러그인) | `docker compose version` |

- Gradle은 따로 설치하지 않는다. 저장소에 포함된 래퍼(`./gradlew`)가 9.5.1을 자동으로 받는다.
- JDK 21이 없어도 Gradle 툴체인이 내려받을 수 있지만, IDE 실행을 위해 로컬에 21을 설치해 두는 편이 편하다.
- Docker Compose는 v2 기준이다. `docker-compose`(하이픈) 대신 `docker compose`(공백)를 쓴다.

---

## 2. 클론

```bash
git clone <repository-url> petopia && cd petopia
```

---

## 3. 인프라 컨테이너 실행 (MySQL + Redis)

로컬 개발용 `docker-compose.yaml`에는 MySQL 8.0과 Redis 7이 정의돼 있다. **애플리케이션은 컨테이너에 포함되지 않는다.** DB/Redis만 컨테이너로 띄우고 앱은 IDE나 Gradle로 직접 실행하는 구성이다.

```bash
docker compose up -d
```

로컬용 compose는 값이 파일에 하드코딩돼 있어 `.env` 없이 바로 뜬다.

| 서비스   | 컨테이너              | 포트   | 계정 / 비밀번호                                        |
|-------|-------------------|------|--------------------------------------------------|
| MySQL | petopia-mysql-local | 3306 | `petopia_user` / `userpassword123!` (DB: `petopia_db`) |
| Redis | petopia-redis-local | 6379 | requirepass `rootpassword123!`                   |

MySQL root 비밀번호는 `rootpassword123!`. 타임존은 둘 다 `Asia/Seoul`, MySQL 문자셋은 `utf8mb4`다.

상태 확인:

```bash
docker compose ps
```

MySQL은 컨테이너가 `Up`이 된 뒤에도 초기화에 10~20초가 더 걸린다. 아래 명령이 통과하면 접속 준비가 끝난 것이다.

```bash
docker exec petopia-mysql-local mysqladmin ping -upetopia_user -puserpassword123!
```

Redis도 함께 확인하려면:

```bash
docker exec petopia-redis-local redis-cli -a 'rootpassword123!' ping
```

> 3306 / 6379 포트를 이미 쓰고 있다면(로컬에 MySQL이 설치돼 있는 경우 등) 컨테이너가 뜨지 않는다. 아래 [문제 해결](#7-문제-해결)을 참고한다.

---

## 4. 애플리케이션 실행

프로필을 지정하지 않으면 `local`로 동작한다(`application.yaml`의 `spring.profiles.default`). `application-local.yaml`의 기본값이 위 compose 설정과 맞춰져 있어, **별도 환경변수 없이 그대로 실행하면 된다.**

```bash
./gradlew bootRun
```

IntelliJ에서는 `PetopiaApplication`을 그냥 실행하면 된다.

기동 로그에 아래 경고가 보이는 것은 정상이다. 마이그레이션 SQL이 아직 없어서 나는 것으로, `V1__init.sql`이 추가되면 사라진다. 자세한 내용은 [src/main/resources/db/migration/README.md](src/main/resources/db/migration/README.md)에 있다.

```
WARN o.f.core.internal.command.DbValidate : No migrations found. Are your locations set up correctly?
```

> **DB가 떠 있어야 앱이 뜬다.** Flyway가 기동 시점에 DB에 접속하기 때문에, 컨테이너를 올리지 않은 채 실행하면 애플리케이션과 `@SpringBootTest`가 함께 실패한다.

---

## 5. 동작 확인

서버는 8080 포트로 뜬다. 인프라 배선(로깅 필터 / DB / Redis) 점검용 API가 있다.

```bash
curl http://localhost:8080/api/reservation/health
```

DB와 Redis가 모두 정상이면 200, 하나라도 실패하면 503이 떨어진다.

| 메서드  | 경로                              | 확인 대상                       |
|------|---------------------------------|-----------------------------|
| GET  | `/api/reservation/health`       | DB + Redis 동시 점검            |
| GET  | `/api/reservation/health/db`    | DUAL 조회 (MyBatis)           |
| GET  | `/api/reservation/health/redis` | Redis SET/GET/DEL           |
| GET  | `/api/reservation/health/logs`  | 레벨별 로그 출력                   |
| POST | `/api/reservation/health/echo`  | 요청 바디 로깅 / 마스킹 확인           |
| GET  | `/api/reservation/health/error` | 예외 로깅 + GlobalExceptionHandler |

Postman을 쓴다면 [postman/petopia.postman_collection.json](postman/petopia.postman_collection.json)을 임포트한다. `baseUrl`이 `http://localhost:8080`으로 잡혀 있다.

테스트 실행:

```bash
./gradlew test
```

---

## 6. 참고 사항

### 컨테이너 정리

```bash
docker compose down
```

데이터까지 지우려면 `docker compose down -v`를 쓴다. 현재 compose에는 named volume이 없어 컨테이너를 지우면 DB 데이터도 함께 사라진다. 스키마는 Flyway가 다시 만들어 준다.

### 스키마 변경

DB에 직접 DDL을 치지 않는다. 전부 `src/main/resources/db/migration`의 SQL 파일로만 한다. 파일명 규칙과 주의사항은 해당 디렉터리의 README에 정리돼 있다.

### `.env`는 로컬에 필요 없다

`.env` / `.env.example` 와 `docker-compose.release.yaml`은 **운영 배포용**이다. 로컬 개발에는 쓰이지 않는다. 운영 환경을 구성할 때만 아래처럼 복사해 값을 채운다.

```bash
cp .env.example .env
```

`.env`는 커밋하지 않는다(`.gitignore`). 어떤 키가 필요한지는 `.env.example`로 관리한다.

### 프로필

| 프로필       | 설정 파일                       | 용도                                            |
|-----------|-----------------------------|-----------------------------------------------|
| `local`   | `application-local.yaml`    | 기본값. compose 설정이 하드코딩돼 있어 그대로 실행 가능. SQL 로그 출력 |
| `release` | `application-release.yaml`  | 운영. 모든 값을 `.env` 환경변수에서 읽음. SQL 로그 미출력          |

`release`로 실행할 때는 `--spring.profiles.active=release`를 붙인다.

---

## 7. 문제 해결

**포트 충돌 (`port is already allocated`)**

3306 또는 6379를 이미 다른 프로세스가 쓰고 있다. 점유 프로세스를 확인한다.

```bash
lsof -i :3306
```

로컬에 설치된 MySQL이라면 중지하거나(`brew services stop mysql`), `docker-compose.yaml`의 호스트 포트를 `"3307:3306"`처럼 바꾼다. 포트를 바꿨다면 실행 시 `DB_PORT` 환경변수도 함께 넘겨야 앱이 같은 곳을 바라본다.

**기동 시 `Communications link failure` / Flyway 연결 오류**

MySQL 컨테이너가 아직 초기화 중이다. 위의 `mysqladmin ping`이 통과할 때까지 기다린 뒤 다시 실행한다.

**Redis `NOAUTH` 또는 인증 실패**

compose의 `--requirepass` 값과 `application-local.yaml`의 `spring.data.redis.password`가 어긋난 경우다. 둘 다 `rootpassword123!`이어야 한다.

**컨테이너 로그 확인**

```bash
docker compose logs -f mysql
```
