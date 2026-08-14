# 예약·결제 대규모 트래픽 대응 설계

> 작성일: 2026-08-09
> 대상: `api/reservation`, `api/payment` 도메인
> 전제 (2026-08-09 확정)
> - 범위: **대기열(Waiting Room)까지 포함**한 전체 3단 방어
> - 운영: **멀티 인스턴스** (ECS Fargate 다중 태스크)
> - 내부 통신: **계약 인터페이스는 유지하되 인프로세스 호출로 교체**
>
> **진행 상황 (2026-08-14 갱신)**
>
> | Phase | 상태 |
> |---|---|
> | 1 정원 카운터 스키마 | ✅ 반영 — 파일명은 **`V22__reservation_capacity_counter.sql`** |
>
> 마이그레이션 번호가 계획서의 V14에서 V22까지 밀렸다. V14·V15는 다른 작업이 선점했고,
> dev 병합으로 V20까지 찼으며, V21은 `feature/BE/payment-method-update` 가 잡고 있다.
>
> ⚠️ **병합 순서 주의**: `out-of-order: false` 라서, V22가 공용 DB에 먼저 적용된 뒤
> V21(결제수단 상세)이 병합되면 그쪽이 기동에 실패한다. **V21 브랜치를 먼저 병합**하거나,
> 늦어지면 이 마이그레이션을 다시 뒤 번호로 옮긴다.
> | 2 정원 차감 원자화 (L3) | ✅ 반영 |
> | 7 대기열 (L1) | ✅ 반영 — 단, 게이트는 **예약 생성 경로만**. 아래 7-4 참고 |
> | 7 대기열 설정 | ✅ **DB로 이전** (`V23__waiting_room_policies.sql`) — 계획서 8장의 "행사별 activeLimit 관리 UI" 후속 과제를 앞당겼다. 아래 7-8 참고 |
> | 0, 3, 4, 5, 6, 8, 9 | ⬜ 미착수 |
>
> Phase 6(L2 Redis 좌석 캐시)을 건너뛰고 7을 먼저 넣었다. L3만으로도 임계구간이
> `COUNT(*)` 포함 수십 ms에서 UPDATE 한 문장으로 줄어들고, 그 위에 대기열이 유입을
> 막으므로 L2 없이도 목표 처리량에 도달하는지 먼저 측정하기로 했다.
>
> **Phase 0(계측)이 아직 없다는 점이 지금 가장 큰 공백이다.** 개선 폭을 숫자로
> 말할 수 없는 상태다.

---

## 0. 결론부터

지금 구조는 **정합성은 맞지만 처리량이 낮다.** 정원 초과나 중복 결제가 나는 구조는 아니다.
문제는 정확성을 얻는 방식이 전부 "임계구간을 길게 잡는 것"에 의존한다는 점이다.

목표는 **정확성을 유지한 채 임계구간을 없애는 것**이고, 방법은 세 겹으로 나눈다.

| 계층 | 하는 일 | 통과 요청 수 | 정확성 책임 |
|---|---|---|---|
| **L1 대기열** | 오픈 직후 유입 자체를 제한 | 동시 활성 N명 | 없음 (유량 조절만) |
| **L2 Redis 좌석 카운터** | 명백히 매진인 요청을 DB 앞에서 차단 | ≈ 정원 수 | 없음 (과다 허용 허용) |
| **L3 DB 조건부 UPDATE** | 정원을 최종 판정 | 정확히 정원 수 | **여기가 유일한 진실** |

핵심 원칙 하나만 기억하면 된다. **L1·L2는 성능 장치이고, 정확성은 오직 L3가 책임진다.**
그래서 Redis가 죽어도, 대기열이 오작동해도 정원이 깨지지 않는다.

---

## 1. 현재 구조 분석

### 1-1. 예약 생성 경로 (`ReservationService.create`)

```
BEGIN TX
  ├ selectCreationContextForUpdate   ← fair_dates 행을 FOR UPDATE OF fd 로 잠금  ★락 시작
  ├ validateFair
  ├ existsActiveReservation          ← reservations 조회
  ├ countCapacityOccupyingReservations ← reservations COUNT(*)   ★예약 수에 비례
  ├ selectUserSnapshot               ← users 조회
  ├ insertReservation
  ├ insertCreatedHistory
  └ entryQrService.issueForReservation (무료 예약만, INSERT 1건)
COMMIT                                                            ★락 해제
```

같은 `(fair_id, operation_date)` 로 들어온 요청은 **전부 이 구간을 한 줄로 통과**한다.
그리고 그 구간 안에 `COUNT(*)` 가 들어 있다. 예약이 1건일 때와 5만 건일 때의 락 보유
시간이 다르다 — 즉 **팔릴수록 느려진다.** 오픈 직후 트래픽이 몰리는 시점과 정확히 반대다.

여기에 두 설정이 겹친다.

- `mybatis.configuration.default-statement-timeout: 3` (초)
- `spring.datasource.hikari.connection-timeout: 3000` (ms), `maximum-pool-size: 10`

락 대기가 3초를 넘기는 순간 statement timeout 으로 죽고, 대기 중인 요청이 커넥션 10개를
전부 물고 있으면 그 뒤 요청은 커넥션 획득 단계에서 3초 후 실패한다. **점진적 저하가 아니라
절벽형 실패**가 나는 조합이다.

### 1-2. 결제 승인 경로 (`PaymentService.confirmPayment`)

```
selectById → 소유자·상태 검증
markProcessing (PENDING→PROCESSING CAS)   ← 여기까지 잘 되어 있음
tossPaymentClient.confirmPayment(...)      ← 외부 호출, 수백 ms ~ 수 초
markCompleted
notifyReservationDomain(row)               ← ★ localhost:8080 으로 자기 자신 HTTP 호출
   └ 실패 시 Thread.sleep(200) 후 재시도 ×3
```

`notifyReservationDomain` 이 호출하는 `ReservationPaymentContractClient` 는
`petopia.reservation.internal-base-url` 기본값이 `http://localhost:8080` 이다.
같은 JVM 안의 컨트롤러를 HTTP로 다시 부른다.

- 요청 1건이 **톰캣 워커 스레드 2개**를 점유한다 (호출한 쪽 + 호출받은 쪽).
- 워커 스레드가 포화되면 호출받는 쪽 스레드를 못 얻고, 호출한 쪽은 그 스레드를 쥔 채
  read timeout(5초)까지 대기한다. **스레드풀 자기교착**이다.
- `payReservationDeposit` 의 `getPaymentContext` 도 같은 경로다.

재시도 실패 시 코드 주석에 적힌 대로 **예약 확정이 영구 누락**되고 보정 수단이 없다.
사용자는 돈을 냈는데 예약이 `PENDING_PAYMENT` 로 남아 있다가 10분 뒤 만료 배치에
`EXPIRED` 처리된다. 이 프로젝트에서 가장 비싼 버그다.

### 1-3. 조회 경로 (`selectAvailabilityDates`)

오픈 직후 가장 많이 호출되는 API인데 `fair_dates LEFT JOIN reservations` + `GROUP BY COUNT` 이다.
예약이 쌓일수록 무거워지고, 정원 차감 경로와 **같은 행들을 훑어 경합까지 만든다.**

### 1-4. 병목 정리

| # | 위치 | 문제 | 심각도 |
|---|---|---|---|
| 1 | `ReservationService.create` | 락 임계구간에 `COUNT(*)` 포함, 전 요청 직렬화 | 치명 |
| 2 | `ReservationPaymentContractClient` | localhost HTTP 자기호출 → 스레드 2배 점유·자기교착 | 치명 |
| 3 | `notifyReservationDomain` | 요청 스레드에서 동기 재시도 + `Thread.sleep`, 실패 시 보정 없음 | 치명 |
| 4 | `selectAvailabilityDates` | 핫 조회에 JOIN+GROUP BY, 캐시 없음 | 높음 |
| 5 | `hikari max-pool 10` / `statement-timeout 3s` | 절벽형 실패 유발 | 높음 |
| 6 | `ReservationExpirationJob` | 멀티 인스턴스에서 중복 실행 (SKIP LOCKED 덕에 안전하나 낭비) | 중간 |
| 7 | 유입 제어 없음 | 오픈 순간 유입을 백엔드가 그대로 다 받음 | 높음 |

### 1-5. 지켜야 할 불변식

어떤 변경을 하든 이 네 가지는 깨지면 안 된다. 모든 Phase의 테스트는 이걸 검증한다.

| ID | 불변식 | 현재 보장 수단 |
|---|---|---|
| **INV-1** | 운영일별 확정 예약 수 ≤ `capacity` | `FOR UPDATE` + `COUNT(*)` |
| **INV-2** | 한 사용자는 한 행사에 활성 예약 1건 | `UK_RESERVATION_ACTIVE_USER_FAIR` (생성 컬럼 `active_key`) |
| **INV-3** | 한 결제 건은 토스 승인을 한 번만 호출 | `markProcessing` CAS + `UK_PAYMENT_IDEMPOTENCY_KEY` |
| **INV-4** | 성공한 결제는 반드시 예약 확정으로 이어짐 | **현재 미보장** (재시도 3회 후 포기) |

---

## 2. 목표 아키텍처

```
                    ┌──────────────────────────────────────────┐
   오픈 직후 유입 →  │ L1  대기열 (Redis ZSET)                  │
                    │     활성 슬롯 N개만 통과, 나머지 대기번호  │
                    └──────────────┬───────────────────────────┘
                                   │  X-Waiting-Token
                    ┌──────────────▼───────────────────────────┐
                    │ L2  좌석 카운터 (Redis, Lua)             │
                    │     DECR > 0 이면 통과, 아니면 즉시 매진   │
                    │     ※ 과다 허용 OK / 장애 시 통과(fail-open)│
                    └──────────────┬───────────────────────────┘
                                   │
                    ┌──────────────▼───────────────────────────┐
                    │ L3  DB 조건부 UPDATE  ★유일한 진실        │
                    │  UPDATE fair_dates SET reserved_count+1   │
                    │   WHERE ... AND reserved_count < capacity │
                    │     → 0건이면 매진                        │
                    └──────────────────────────────────────────┘
```

### 왜 이 조합인가

- **L3만으로도 정합성은 완벽하다.** `WHERE reserved_count < capacity` 와 `SET reserved_count+1` 은
  같은 문장이라 그 사이에 다른 트랜잭션이 끼어들 수 없다. 애플리케이션 락이 필요 없다.
- **L3만 쓰면 핫 로우 경합이 남는다.** 같은 `fair_dates` 행을 모든 요청이 UPDATE 하므로
  결국 직렬화된다. 다만 임계구간이 `COUNT(*)` 포함 수십 ms → UPDATE 한 문장 수십 μs 로 줄어
  처리량이 자릿수 단위로 올라간다.
- **L2가 그 경합마저 없앤다.** 매진 이후의 요청은 DB 커넥션을 잡지도 않는다.
  정원이 1만이면 DB에 도달하는 요청은 대략 1만 건 + α 뿐이다.
- **L1은 L2조차 감당 못 하는 순간 유입**(오픈 0초에 수십만 동시 접속)을 위한 것이다.
  Redis도 커넥션 풀이 있고 앱 스레드도 유한하다.

---

## 3. Phase별 상세 계획

Phase 순서는 **위험이 큰 것부터**다. 대기열(L1)이 가장 화려하지만 가장 나중이다 —
그 아래가 튼튼하지 않으면 대기열은 문제를 미룰 뿐이다.

| Phase | 내용 | 선행 | 배포 단위 |
|---|---|---|---|
| 0 | 계측·부하테스트 기반 | - | 별도 |
| 1 | DB 스키마 (V22) | 0 | 마이그레이션 단독 배포 |
| 2 | 정원 차감 원자화 (L3) | 1 | 배포 1 |
| 3 | 내부 통신 인프로세스화 | - | 배포 1 |
| 4 | 결제완료 통지 비동기 + 보정 배치 | 3 | 배포 1 |
| 5 | 분산락 + 스케줄러 단일 실행 | - | 배포 1 |
| 6 | Redis 좌석 선점 (L2) | 2 | 배포 2 (플래그) |
| 7 | 대기열 (L1) | 6 | 배포 3 (플래그) |
| 8 | 커넥션풀·스레드 튜닝 | 2,3 | 배포 2 |
| 9 | 부하 테스트 검증 | 전부 | - |

---

### Phase 0 — 계측·부하테스트 기반

**목적**: "빨라졌다"를 감이 아니라 숫자로 말하기 위한 기준선.

**작업**
1. `spring-boot-starter-actuator` + Micrometer 추가 (`build.gradle`)
   - `/actuator/health` (ALB 헬스체크용, 기존 `/api/reservation/health` 와 분리 — ECS 문서 3번 지적사항과 동일)
   - `/actuator/prometheus` 노출, 인증 필터에서 내부망만 허용
2. 커스텀 메트릭 정의

| 메트릭 | 타입 | 태그 | 의미 |
|---|---|---|---|
| `reservation.create.duration` | Timer | `result` | 예약 생성 처리 시간 |
| `reservation.create.result` | Counter | `result=success/sold_out/duplicated/error` | 결과 분포 |
| `reservation.capacity.occupy.conflict` | Counter | `fair_id` | L3에서 매진으로 떨어진 수 |
| `seat.inventory.result` | Counter | `result=hit/miss/sold_out/fallback` | L2 동작 분포 |
| `waiting.room.active` / `waiting.room.queue_length` | Gauge | `fair_id` | 대기열 상태 |
| `payment.confirm.duration` | Timer | `result` | 토스 승인 포함 결제 처리 |
| `payment.notify.result` | Counter | `result=ok/retry/recovered` | 예약 통지 성공률 |
| `hikari.connections.pending` | (기본 제공) | - | 커넥션 대기 |

3. k6 부하 시나리오 스켈레톤을 `loadtest/` 에 추가 (Phase 9에서 사용)

**산출물**: `loadtest/reservation-open.js`, `docs/loadtest-baseline.md` (측정 기준선)

---

### Phase 1 — DB 스키마: `V22__reservation_capacity_counter.sql`

**목적**: 정원 점유 수를 `COUNT(*)` 가 아니라 물리 컬럼으로 들어 O(1)로 만든다.

```sql
ALTER TABLE `fair_dates`
    ADD COLUMN `reserved_count` INT NOT NULL DEFAULT 0
        COMMENT '정원을 점유 중인 사전예약 수' AFTER `capacity`,
    ADD CONSTRAINT `CK_FAIR_DATE_RESERVED_COUNT` CHECK (`reserved_count` >= 0);

-- 백필. 점유 판정 기준은 애플리케이션과 동일하게 맞춘다.
UPDATE `fair_dates` fd
   SET fd.`reserved_count` = (
        SELECT COUNT(*) FROM `reservations` r
         WHERE r.`fair_id` = fd.`fair_id`
           AND r.`visit_date` = fd.`operation_date`
           AND r.`reservation_type` = 'ADVANCE'
           AND r.`status` IN ('PENDING_PAYMENT','CONFIRMED','CHECKED_IN')
   );

-- Phase 4 보정 배치용: "COMPLETED 예약금 결제" 를 paid_at 순으로 훑는다.
ALTER TABLE `payment`
    ADD KEY `idx_payment_type_status_paid` (`payment_type`, `status`, `paid_at`);
```

**주의사항**
- `reserved_count` 는 **`reservation_type='ADVANCE'` 만** 센다. 현장판매(`ONSITE`)는 기존에도
  정원 대상이 아니었고(`countCapacityOccupyingReservations` 의 WHERE 절), 이 규칙을 그대로 유지한다.
- `CHECK` 제약은 MySQL 8.0.16+ 에서 실제로 강제된다. 반납 UPDATE는 반드시
  `WHERE reserved_count > 0` 을 함께 걸어 위반이 나지 않게 한다.
- 백필 UPDATE는 `fair_dates` 전체를 훑는다. 운영 데이터가 커지기 전인 지금 하는 게 유리하다.
- **이 마이그레이션은 단독으로 먼저 배포한다.** 컬럼만 추가된 상태에서는 아무 코드도 이걸
  읽지 않으므로 무해하고, Phase 2 배포 시 롤백해도 컬럼은 남아 있어 안전하다.

**롤백**: 컬럼을 지우지 않는다. Phase 2 코드만 되돌리면 기존 `COUNT(*)` 경로로 복귀한다.

---

### Phase 2 — 정원 차감 원자화 (L3)

**목적**: `FOR UPDATE` + `COUNT(*)` 임계구간 제거. INV-1을 DB 한 문장으로 옮긴다.

#### 2-1. 새 매퍼: `ReservationCapacityMapper`

`src/main/java/com/ms/petopia/api/reservation/mapper/ReservationCapacityMapper.java`
`src/main/resources/mapper/reservation/ReservationCapacityMapper.xml`

```java
int occupy(Long fairId, LocalDate visitDate);   // 1 = 성공, 0 = 매진
int release(Long fairId, LocalDate visitDate);  // 1 = 반납, 0 = 이미 0
int reconcileReservedCount(Long fairId, LocalDate fromDate); // 정합성 보정
List<FairDateCapacityRow> selectCapacities(Long fairId, LocalDate fromDate); // L2 시드용
FairDateCapacityRow selectCapacity(Long fairId, LocalDate visitDate);
```

```xml
<update id="occupy">
    UPDATE fair_dates
       SET reserved_count = reserved_count + 1, updated_at = NOW()
     WHERE fair_id = #{fairId} AND operation_date = #{visitDate}
       AND reserved_count &lt; capacity
</update>

<update id="release">
    UPDATE fair_dates
       SET reserved_count = reserved_count - 1, updated_at = NOW()
     WHERE fair_id = #{fairId} AND operation_date = #{visitDate}
       AND reserved_count &gt; 0
</update>
```

`release` 의 `reserved_count > 0` 은 CHECK 제약 방어이자 **중복 반납 방어**다.
같은 예약을 두 번 취소 처리해도 음수로 내려가지 않는다.

#### 2-2. `ReservationService.create` 재구성

```
BEGIN TX
  ├ selectCreationContext          ← FOR UPDATE 제거. 잠그지 않는다
  ├ validateFair / validateUser / validateTerms   ← 전부 락 밖
  ├ existsActiveReservation
  ├ capacityMapper.occupy(...)     ← ★락 시작. 0이면 SOLD_OUT
  ├ insertReservation              ← DuplicateKey → DUPLICATED_RESERVATION
  ├ insertCreatedHistory
  └ entryQrService.issue... (무료만)
COMMIT                              ★락 해제
```

- `selectCreationContextForUpdate` → `selectCreationContext` 로 이름 변경, `FOR UPDATE OF fd` 삭제.
  여기서 읽는 `capacity` 는 **응답용·사전검증용 참고값**이지 정원 초과를 막는 근거가 아니다.
- `countCapacityOccupyingReservations` 는 `ReservationMapper` 에서 제거(보정 SQL로 흡수).
- 락 보유 구간이 `occupy → INSERT ×2 → (QR INSERT) → COMMIT` 으로 축소된다.
  **정원 확인이 락 안에서 사라진 것이 핵심**이다.
- 트랜잭션이 롤백되면 `occupy` 도 함께 롤백되므로 별도 보상이 필요 없다. (L2는 다름 — Phase 6)

#### 2-3. 반납·이동 경로 (누락하면 좌석이 영구 증발한다)

`reservations.status` 를 바꾸는 곳은 5군데뿐이다. 각각 대응을 정한다.

| 파일 | 전이 | 카운터 처리 |
|---|---|---|
| `ReservationCancellationService` | `PENDING_PAYMENT`/`CONFIRMED` → `CANCELED` | `release` (단, `ADVANCE` 일 때만) |
| `ReservationExpirationService` | `PENDING_PAYMENT` → `EXPIRED` | `release` (건별) |
| `ReservationVisitDateChangeService` | `visit_date` 변경 | 대상일 `occupy` + 원래일 `release` |
| `ReservationPaymentCompletionService` | `PENDING_PAYMENT` → `CONFIRMED` | **없음** (둘 다 점유 상태) |
| `EntryMapper` (게이트 입장) | `CONFIRMED` → `CHECKED_IN` | **없음** (둘 다 점유 상태) |

세부:

- **취소**: `cancelReservation` 이 1을 반환한 직후 같은 트랜잭션에서 `release`.
  `ReservationCancellationContext` 에 `reservationType` 이 이미 있으므로 `ADVANCE` 판별 가능.
- **만료**: `selectDueReservationsForUpdate` 결과에 `visit_date` 를 추가해야 한다
  (현재 `reservation_id`, `fair_id` 만 반환). `ExpiringReservationRow` 에 필드 추가.
  건별로 `expirePendingReservation` 이 1을 반환할 때만 `release`.
- **날짜 변경**: 이 경로만 `fair_dates` 두 행을 동시에 만진다. **교착 위험**이 있으므로
  기존 `selectFairDatesForUpdate` 의 `ORDER BY operation_date ASC FOR UPDATE` 를 **유지**한다.
  이미 날짜 오름차순으로 잠그고 있어 반대 방향 요청끼리도 교착되지 않는다. 저빈도 경로라
  락을 남겨도 처리량에 영향이 없다. 이 조회에 `reserved_count` 를 추가해
  `countCapacityOccupyingAdvanceReservations` 를 대체한다.

#### 2-4. 조회 경로 경량화

`selectAvailabilityDates` 에서 JOIN·GROUP BY 제거:

```xml
SELECT fd.operation_date AS visit_date, fd.entry_start_time, fd.entry_end_time,
       fd.capacity, fd.reserved_count AS occupied_count
  FROM fair_dates fd
 WHERE fd.fair_id = #{fairId} AND fd.operation_date > #{today}
 ORDER BY fd.operation_date ASC
```

`ReservationAvailabilityDateRow` / `ReservationAvailabilityService` 는 그대로 쓸 수 있다.

#### 2-5. 정합성 보정 장치

카운터는 상태 전이와 같은 트랜잭션에서만 움직이므로 정상적으로는 어긋나지 않는다.
다만 **운영 중 수동 SQL로 예약 상태를 고치는 일**은 실제로 일어나므로 되돌릴 수단을 남긴다.

```xml
<update id="reconcileReservedCount">
    UPDATE fair_dates fd
       SET fd.reserved_count = (
            SELECT COUNT(*) FROM reservations r
             WHERE r.fair_id = fd.fair_id AND r.visit_date = fd.operation_date
               AND r.reservation_type = 'ADVANCE'
               AND r.status IN ('PENDING_PAYMENT','CONFIRMED','CHECKED_IN'))
         , fd.updated_at = NOW()
     WHERE fd.fair_id = #{fairId} AND fd.operation_date &gt;= #{fromDate}
</update>
```

관리자 API 또는 운영 스크립트에서 행사 단위로 호출한다. 자동 주기 실행은 하지 않는다 —
이 UPDATE는 무거운 데다, 자동으로 덮어쓰면 **어긋났다는 사실 자체가 안 보이게 된다.**
대신 Phase 0 메트릭에 드리프트 감지용 게이지를 두고 알림으로 잡는다.

#### 2-6. 테스트

| 테스트 | 검증 |
|---|---|
| `ReservationServiceTest` (기존 수정) | `occupy` 0 반환 시 `RESERVATION_SOLD_OUT` |
| `ReservationCapacityConcurrencyTest` (신규, Testcontainers 또는 로컬 MySQL) | 정원 100에 300 스레드 동시 요청 → 성공 정확히 100, `reserved_count`=100 (INV-1) |
| 동일 테스트 | 실패 200건이 전부 `SOLD_OUT` 이고 롤백되어 `reservations` 에 흔적 없음 |
| `ReservationCancellationServiceTest` | 취소 후 `reserved_count` 감소, 재예약 가능 |
| `ReservationExpirationServiceTest` | 만료 후 `reserved_count` 감소 |
| `ReservationVisitDateChangeServiceTest` | 날짜 이동 시 두 카운터가 각각 ±1 |
| `ReservationMapperSqlContractTest` (기존) | 매퍼 시그니처 변경 반영 |

---

### Phase 3 — 예약↔결제 내부 통신 인프로세스화

**목적**: 톰캣 스레드 2배 점유와 자기교착 제거. 병목 #2.

**설계**: 계약(인터페이스)은 남기고 구현만 바꾼다.

```
api/payment/client/
  ReservationPaymentContract.java          (신규 인터페이스 — 기존 클라이언트의 public 메서드 그대로)
  InProcessReservationPaymentContract.java (신규 @Primary 구현 — 예약 서비스 직접 호출)
  RestReservationPaymentContract.java      (기존 ReservationPaymentContractClient 를 리네임, @ConditionalOnProperty)
```

```java
public interface ReservationPaymentContract {
    ReservationPaymentContext getPaymentContext(Long reservationId);
    ReservationPaymentCompletionResult completePayment(
            String eventId, Long paymentId, Long reservationId, Long paidAmount, LocalDateTime paidAt);
}
```

인프로세스 구현은 `ReservationPaymentContextService` / `ReservationPaymentCompletionService` 를
직접 주입받아 호출하고, 예약 도메인 DTO(`ReservationPaymentContextResponse`)를 결제 도메인
DTO(`ReservationPaymentContext`)로 옮겨 담는다.

**도메인 경계는 유지된다**
- 결제 도메인은 여전히 인터페이스에만 의존한다. 예약 매퍼나 테이블을 직접 보지 않는다.
- `/internal/api/v1/...` REST 엔드포인트는 **삭제하지 않는다.** 나중에 서비스를 분리하면
  구현만 `Rest*` 로 바꾸면 된다. 전환 스위치는 프로퍼티로 둔다:
  `petopia.reservation.contract-mode: in-process | rest` (기본 `in-process`)

**예외 매핑 주의**
현재 REST 구현은 4xx를 전부 `PAYMENT_TARGET_NOT_PAYABLE` 로 뭉갠다. 인프로세스로 바꾸면
예약 도메인의 `CommonException`(`RESERVATION_PAYMENT_EXPIRED` 등)이 그대로 올라온다.
**동작이 조용히 바뀌면 안 되므로** 인프로세스 구현에서 명시적으로 같은 매핑을 한다.

```java
try { return contextService.getPayableContext(reservationId); }
catch (CommonException e) { throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE, e); }
```

**트랜잭션 주의**
`confirmPayment` 는 의도적으로 `@Transactional` 이 없다(주석에 근거 명시됨). 인프로세스로
바꿔도 이 성질은 유지된다 — `completePayment` 는 `ReservationPaymentCompletionService.complete`
자신의 `@Transactional` 로만 묶인다. **결제 트랜잭션과 예약 트랜잭션을 하나로 합치지 않는다.**
합치면 토스 승인 후 예약 확정 실패 시 결제까지 롤백되어 "돈은 나갔는데 결제 기록이 없는"
더 나쁜 상태가 된다.

**테스트**: `PaymentServiceTest` 의 클라이언트 목을 인터페이스 목으로 교체.
`InProcessReservationPaymentContractTest` 신규 — 예외 매핑이 REST 구현과 동일한지 검증.

---

### Phase 4 — 결제완료 통지 비동기화 + 누락 보정 배치

**목적**: 병목 #3 제거, **INV-4 최초 보장**.

#### 4-1. 요청 스레드에서 통지 분리

`confirmPayment` 는 `markCompleted` 까지만 하고 즉시 응답한다.
통지는 전용 `TaskExecutor` 로 넘긴다.

```java
// PaymentAsyncConfig
@Bean("paymentNotifyExecutor")
ThreadPoolTaskExecutor paymentNotifyExecutor() {
    // core 4 / max 8 / queue 500 / CallerRunsPolicy
    // 큐가 넘치면 호출 스레드에서 실행 — 통지를 버리느니 느려지는 쪽을 택한다.
}
```

- `Thread.sleep(200)` 기반 동기 재시도 **삭제**. 1회만 시도하고 실패하면 로그만 남긴다.
- 재시도는 아래 보정 배치가 전담한다. 두 군데서 재시도하면 추적이 어렵다.
- 응답 지연에서 통지 왕복(최대 3회 × (호출+200ms))이 통째로 빠진다.

**프론트 영향**: `PaymentResultPage` 가 승인 직후 예약 상태를 조회하면 아직 `PENDING_PAYMENT`
일 수 있다. 결제 응답의 `status=COMPLETED` 를 신뢰하게 하고, 예약 상태는 짧은 폴링으로
확인하도록 프론트 계약을 정리한다. (`frontend/src/pages/payment/PaymentResultPage.tsx`)

#### 4-2. 보정 배치: `PaymentCompletionRecoveryJob`

"결제는 `COMPLETED` 인데 예약 확정 영수증이 없는" 건을 찾아 재통지한다.

```sql
SELECT p.payment_id, p.reservation_id, p.amount, p.paid_at
  FROM payment p
  LEFT JOIN reservation_payment_confirmations c ON c.payment_id = p.payment_id
 WHERE p.payment_type = 'RESERVATION_DEPOSIT'
   AND p.status = 'COMPLETED'
   AND p.paid_at &lt; #{until}      -- 방금 처리 중인 건은 건너뛴다 (예: 30초 이전)
   AND c.confirmation_id IS NULL
 ORDER BY p.paid_at
 LIMIT #{limit}
```

`idx_payment_type_status_paid` (Phase 1) 가 이 조회를 받친다.

- 주기: 30초 (`@Scheduled(fixedDelay)`), 배치 크기 100
- `eventId = "PAYMENT_" + paymentId` 로 **원래 통지와 동일**하므로 멱등하다.
  `reservation_payment_confirmations` 의 `UK_RESERVATION_PAYMENT_EVENT` 가 중복을 막는다.
- 분산락 필수 (Phase 5)

**핵심 경계 조건 — 만료된 예약**
`complete()` 는 `paidAt >= paymentExpiresAt` 이면 `RESERVATION_PAYMENT_EXPIRED` 를 던진다.
통지가 지연되는 동안 만료 배치가 예약을 `EXPIRED` 로 바꾼 경우, **재통지해도 영원히 실패**한다.
`paid_at` 은 실제 결제 시각이므로 제한시각 이전이면 통과해야 맞다. 따라서:

- `ReservationPaymentCompletionService.complete` 에서 `EXPIRED` 상태라도
  **`paidAt < paymentExpiresAt` 이면 확정으로 되살린다**(`EXPIRED → CONFIRMED`).
  이건 "제한시간 내에 결제한 사람은 예약을 받는다"는 원래 정책과 일치한다.
- 되살릴 때 `reserved_count` 를 다시 `occupy` 해야 한다. 만료 시 이미 반납했기 때문이다.
  `occupy` 가 0을 반환하면(그 사이 다른 사람이 채웠다면) **환불 대상**으로 표시하고
  `RESERVATION_SOLD_OUT_AFTER_PAYMENT` 같은 별도 에러코드로 남긴다. 자동 환불은 별도 과제.
- `paidAt >= paymentExpiresAt` 인 진짜 지각 결제는 기존대로 실패시키고 **환불 큐**로 보낸다.

> 이 경계 조건이 Phase 4에서 제일 까다롭다. 별도 테스트 케이스로 못박는다.

- 3회 이상 실패한 건은 `WARN` 이 아니라 `ERROR` + 알림. 사람이 봐야 하는 상태다.

#### 4-3. 테스트

| 테스트 | 검증 |
|---|---|
| `PaymentServiceTest` | 통지 실패해도 `confirmPayment` 는 성공 응답 |
| `PaymentCompletionRecoveryJobTest` | 누락 건 탐지 → 재통지 → 확정 |
| 동일 | 이미 확정된 건은 조회 대상에서 제외 |
| `ReservationPaymentCompletionServiceTest` | `EXPIRED` + `paidAt < expiresAt` → `CONFIRMED` 로 복구, 카운터 재점유 |
| 동일 | `EXPIRED` + 정원 소진 → 환불 대상 에러 |
| 동일 | `paidAt >= expiresAt` → 기존대로 `RESERVATION_PAYMENT_EXPIRED` |

---

### Phase 5 — Redis 분산락 + 스케줄러 단일 실행

**목적**: 멀티 인스턴스에서 `@Scheduled` 가 태스크 수만큼 중복 실행되는 것을 막는다.

```java
// global/lock/RedisDistributedLock.java
public boolean tryLock(String key, String token, Duration ttl);  // SET key token NX PX ttl
public void unlock(String key, String token);                    // Lua: 내 토큰일 때만 DEL
public <T> Optional<T> runIfAcquired(String key, Duration ttl, Supplier<T> task);
```

`unlock` 은 반드시 Lua로 토큰 비교 후 삭제한다. 그냥 `DEL` 하면 TTL 만료 후 다른 인스턴스가
잡은 락을 지워버린다.

**적용 대상**

| 잡 | 락 키 | TTL |
|---|---|---|
| `ReservationExpirationJob` | `lock:reservation:expiration` | 30s |
| `PaymentCompletionRecoveryJob` | `lock:payment:completion-recovery` | 60s |

- TTL은 잡의 최대 실행 시간보다 **넉넉히** 잡되, 주기보다는 짧게 둔다.
- 락 획득 실패 시 조용히 스킵(`DEBUG` 로그). 정상 동작이다.
- **락은 성능 최적화일 뿐 정합성 근거가 아니다.** 만료 배치는 `FOR UPDATE SKIP LOCKED` +
  `WHERE status='PENDING_PAYMENT'` 로 이미 중복 안전하고, 보정 배치는 `event_id` UK로 멱등하다.
  Redis가 죽어도 **중복 실행될 뿐 잘못되지 않는다.** → 락 실패 시 fail-open(그냥 실행)으로 둔다.

**테스트**: `RedisDistributedLockTest` — 동시 10스레드 중 1개만 획득, 남의 락 unlock 불가, TTL 만료 후 재획득.

---

### Phase 6 — Redis 좌석 선점 (L2)

**목적**: 매진 이후 요청이 DB 커넥션을 잡지 못하게 한다. 핫 로우 경합 제거.

#### 6-1. `SeatInventoryStore`

키: `seat_inventory:{fairId}:{yyyy-MM-dd}` → 잔여 좌석 정수. TTL 10분(슬라이딩).

```lua
-- OCCUPY: 1=성공, 0=매진, -1=키없음(시드 필요)
local remaining = redis.call('GET', KEYS[1])
if remaining == false then return -1 end
if tonumber(remaining) <= 0 then return 0 end
redis.call('DECR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[1])
return 1
```

```lua
-- RELEASE: 정원(ARGV[2])을 넘지 않는 선에서 +1. 키 없으면 아무것도 안 함
local remaining = redis.call('GET', KEYS[1])
if remaining == false then return 0 end
if tonumber(remaining) >= tonumber(ARGV[2]) then return 0 end
redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[1])
return 1
```

시드는 `SET key value NX EX ttl` — 동시 시드가 나도 먼저 쓴 값만 남고 그 사이 진행된
선점이 덮어써지지 않는다.

#### 6-2. 오차를 어떻게 다루는가 (이 Phase의 핵심)

| 상황 | 결과 | 대응 |
|---|---|---|
| 시드 시점과 DB 커밋 시점이 어긋남 | **과다 허용** (실제보다 여유 있게 봄) | 통과한 요청이 L3에서 매진 판정. 정합성 무해 |
| Redis 다운 / 타임아웃 | 선점 생략, 전부 DB로 | **fail-open**. 느려질 뿐 예약은 계속됨 |
| 반납 누락 (앱 크래시 등) | **과소 허용** (팔 수 있는 좌석을 못 팜) | TTL 만료 후 DB 재시드로 자동 회복 |
| 정원(capacity)을 관리자가 변경 | 카운터가 옛 값 | 정원 수정 API에서 `invalidate(fairId, date)` 호출 |

**과다 허용은 허용, 과소 허용은 최대한 회피** — 이게 이 계층의 설계 기준이다.
매진을 잘못 표시하는 건 곧 매출 손실이지만, 통과시켰다가 DB에서 걸리는 건 사용자에게
"방금 매진됐습니다" 를 보여주는 것뿐이다.

#### 6-3. 예약 생성 흐름 통합

```
create():
  ├ 사전 검증 (읽기)
  ├ seatInventoryStore.tryOccupy(fairId, visitDate, () -> DB 잔여)
  │     └ false → RESERVATION_SOLD_OUT  ★DB 트랜잭션 시작 전에 끝
  ├ 트랜잭션 롤백 시 Redis 반납 훅 등록
  │     TransactionSynchronizationManager.registerSynchronization(
  │         afterCompletion(STATUS_ROLLBACK) -> seatInventoryStore.release(...))
  └ 기존 L3 흐름
```

- 롤백 훅이 `SOLD_OUT`(L3에서 떨어짐), `DUPLICATED_RESERVATION`, 예기치 못한 예외를 **전부** 커버한다.
- 취소/만료/날짜변경에서는 `release` 대신 **`invalidate`**(키 삭제)를 쓴다.
  정원 상한을 알 필요가 없고, 다음 요청이 DB에서 정확한 값을 다시 읽어오므로 더 안전하다.
  저빈도 경로라 재시드 비용은 무시할 만하다.

#### 6-4. 잔여 좌석 조회

`ReservationAvailabilityService` 에서 운영일별로 `findRemaining` 을 먼저 보고,
`null`(키 없음/Redis 장애)이면 DB의 `capacity - reserved_count` 를 쓴다.
오픈 직후 폴링 트래픽이 DB를 전혀 건드리지 않게 된다.

#### 6-5. 플래그와 롤백

`petopia.reservation.seat-inventory.enabled: true|false` (기본 `true`)
`false` 로 두면 `tryOccupy` 가 항상 `true` 를 반환해 Phase 2 상태와 완전히 동일하게 동작한다.
**재배포 없이 끌 수 있어야 한다** — 환경변수로 노출한다.

#### 6-6. 테스트

| 테스트 | 검증 |
|---|---|
| `SeatInventoryStoreTest` | 시드→선점→매진 시퀀스, 반납 상한, TTL |
| 동일 | Redis 예외 시 `tryOccupy` 가 `true` (fail-open) |
| `ReservationServiceTest` | 선점 실패 시 DB 접근 없이 `SOLD_OUT` |
| 통합 동시성 테스트 | L2 켠 상태에서도 성공 수가 정확히 정원과 일치 (INV-1) |
| 롤백 테스트 | 중복 예약으로 롤백 시 Redis 잔여가 원복 |

---

### Phase 7 — 대기열 (L1)

**목적**: 오픈 순간 유입 자체를 제한한다. L2·L3가 감당 못 하는 유량을 앞에서 잡는다.

#### 7-1. 자료구조

| 키 | 타입 | 내용 |
|---|---|---|
| `waiting:{fairId}:queue` | ZSET | member=토큰, score=발급 시각(ms). 대기 순번 = rank |
| `waiting:{fairId}:active` | ZSET | member=토큰, score=만료 시각. 활성 슬롯 |
| `waiting:{fairId}:token:{token}` | STRING | userId (토큰↔사용자 검증용), TTL |

활성 슬롯 정원 `activeLimit` 는 행사별 설정(기본값 프로퍼티).
활성 슬롯 TTL은 **결제 대기 시간(10분)보다 길게** 잡는다 — 기본 12분.
그래야 예약 → 결제 완료까지 한 슬롯으로 끝난다.

#### 7-2. API

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/fairs/{fairId}/waiting-room/tickets` | 대기 토큰 발급 (인증 필요) |
| `GET` | `/api/fairs/{fairId}/waiting-room/tickets/{token}` | 순번·상태 폴링 |
| `DELETE` | `/api/fairs/{fairId}/waiting-room/tickets/{token}` | 이탈 (슬롯 즉시 반납) |

응답:

```json
{
  "token": "…",
  "status": "WAITING | ADMITTED",
  "position": 1523,
  "ahead": 1522,
  "estimatedWaitSeconds": 91,
  "expiresAt": "2026-08-09T10:12:00"
}
```

`estimatedWaitSeconds` 는 최근 승급 속도(초당 승급 인원)의 이동평균으로 계산한다.
**정확할 필요는 없지만 단조 감소해야 한다** — 숫자가 늘었다 줄었다 하면 사용자가 새로고침을
반복해서 오히려 트래픽이 는다. 계산값이 직전 응답보다 크면 직전 값을 유지한다.

#### 7-3. 승급(promotion)

Lua 한 번으로 원자 처리한다.

```
1. active ZSET 에서 score < now 인 만료 슬롯 제거 (ZREMRANGEBYSCORE)
2. 빈 슬롯 수 = activeLimit - ZCARD(active)
3. queue 에서 앞에서부터 그만큼 꺼내(ZPOPMIN) active 에 넣음(ZADD score=now+ttl)
```

실행 시점: **폴링 요청이 들어올 때마다**(lazy). 별도 스케줄러를 두지 않는다.
아무도 안 기다리면 승급할 이유도 없고, 스케줄러는 인스턴스 수만큼 중복 실행 문제를 부른다.

#### 7-4. 게이트 적용

`WaitingRoomInterceptor` (`HandlerInterceptor`) 로 아래 경로에만 `X-Waiting-Token` 을 요구한다.

| 보호 대상 | 상태 | 이유 |
|---|---|---|
| `POST /api/v1/fairs/{fairId}/reservations` | ✅ 적용 | 예약 생성. 경로에 fairId가 있어 그 행사 슬롯을 정확히 대조한다 |
| `POST /api/reservations/{id}/payment` | ⬜ 보류 | 결제 준비 |
| `POST /api/payments/{id}/confirm` | ⬜ 보류 | 결제 승인 |

> **결제 경로를 보류한 이유 (2026-08-14)**
>
> 1. `PaymentController`가 아직 JWT가 아니라 임시 헤더(`X-User-Id`)로 사용자를 받는다.
>    SecurityContext에 인증 주체가 없어 **토큰↔사용자 대조 자체가 불가능**하고, 게이트를
>    걸어도 조용히 통과할 뿐이라 보안상 착시만 만든다.
> 2. `POST /api/payments/{id}/confirm`은 예약금 전용이 아니다. **참가비·행사개설비 승인도
>    같은 경로**를 쓴다. 여기에 대기 토큰을 요구하면 대기열을 켠 동안 참가업체 결제가
>    통째로 막힌다.
>
> 결제까지 도달하려면 예약 생성을 먼저 통과해야 하므로 결제 유량도 `activeLimit` 안에서
> 간접적으로 제한된다. 결제 도메인이 JWT로 전환되고 승인 경로가 결제유형별로 갈리면
> 그때 예약금 경로만 골라 추가한다.

- 토큰의 `userId` 가 인증 주체와 일치하는지 검증한다. 안 그러면 토큰 하나로 여러 계정이 통과한다.
- 결제 API까지 보호하는 이유: 예약만 통과시키고 결제를 열어두면, 결제 단계에서 토스 호출이
  몰려 같은 문제가 반복된다.
- 통과 시 슬롯 TTL을 연장(sliding)한다.
- **행사 단위로 켠다.** `waiting_room_enabled` 를 `fairs` 에 두거나, 우선은 프로퍼티
  `petopia.waiting-room.enabled-fair-ids` 로 시작한다. 상시 켜둘 기능이 아니다.

#### 7-5. 프론트

`frontend/src/pages/reservation/` 에 대기 화면 추가.
- 토큰을 `sessionStorage` 에 저장(탭 단위). 새로고침해도 순번 유지.
- 폴링 간격은 순번에 비례해 늘린다 — 1000번대는 5초, 100번대는 2초, 10번대는 1초.
  **전원이 1초 폴링하면 대기열 자체가 새로운 부하가 된다.**
- `ADMITTED` 로 바뀌면 예약 페이지로 이동, 이후 모든 요청에 `X-Waiting-Token` 헤더 첨부
  (`frontend/src/api/reservation.ts`, `payment.ts` 인터셉터).

#### 7-6. 실패 모드

| 상황 | 동작 |
|---|---|
| Redis 다운 | **fail-open** — 인터셉터가 전부 통과시킨다. 대기열 없이 L2/L3만으로 동작 |
| 토큰 만료 | 401 + 재발급 안내. 프론트가 자동 재발급 후 대기 화면 복귀 |
| 슬롯 받고 이탈 | TTL(12분) 후 자동 회수. `DELETE` 호출 시 즉시 회수 |
| 활성 슬롯 고갈로 아무도 못 들어옴 | `activeLimit` 을 런타임 조정 가능하게 프로퍼티+환경변수로 노출 |

#### 7-7. 테스트

| 테스트 | 검증 |
|---|---|
| `WaitingRoomServiceTest` | 발급 순서대로 승급, `activeLimit` 초과 승급 없음 |
| 동일 | 만료 슬롯 회수 후 다음 대기자 승급 |
| 동일 | 동시 100 발급 시 순번 중복 없음 |
| `WaitingRoomInterceptorTest` | 토큰 없음/만료/타인 토큰 거부, Redis 장애 시 통과 |
| E2E | 대기 → 승급 → 예약 → 결제 전 구간이 토큰 하나로 완주 |

#### 7-8. 설정을 DB로 (2026-08-14 반영)

원래 계획은 프로퍼티(`enabled-fair-ids`, `active-limit`)로 시작하고 관리 UI는 후속 과제로
미루는 것이었다. 실제로 만들어 보니 **이 값들은 정확히 오픈 순간에 손대야 하는 값들**이었다.
백엔드 지표를 보며 통과 인원을 올리고 내려야 하는데 프로퍼티는 재배포나 재기동을 요구한다.
정작 조정이 필요한 그 순간에 손을 못 대는 셈이라 DB로 옮겼다.

**`waiting_room_policies`** — 행사(`fairs`) 단위 1건. 운영일 단위가 아닌 이유는, 대기열이
정하는 것이 "이 행사 예매창구에 몇 명을 들여보낼지"이고 그 안에서 어느 날짜를 고르는지는
통과한 뒤의 문제이기 때문이다.

| 컬럼 | 의미 |
|---|---|
| `enabled` | 이 행사에 대기열을 적용할지 |
| `active_limit` | 동시 통과 인원. `CHECK (1 ~ 100000)` — 0이면 예매가 완전히 멈춘다 |
| `version` | 낙관적 잠금. 두 관리자가 동시에 조정하면 `409 R023` |

**관리자 API** — `onsite-sales-policy`와 같은 패턴.

| 메서드 | 경로 |
|---|---|
| `GET` | `/api/v1/admin/fairs/{fairId}/waiting-room-policy` |
| `PUT` | `/api/v1/admin/fairs/{fairId}/waiting-room-policy` |

**프로퍼티에 남긴 것**: `active-ttl`, `ticket-ttl`, `default-active-limit`.
TTL은 결제 제한시간(10분)과 맞물려 있어 행사별로 다를 이유가 없고, 잘못 줄이면 결제 중인
사용자가 슬롯을 잃는다. 관리자 화면에 노출하지 않는다.

**캐시가 필수다.** `resolve()`는 예약 요청마다 호출되므로 매번 DB를 읽으면 오픈 러시를
막으려는 장치가 스스로 DB 부하를 만든다. 인스턴스 로컬 10초 TTL 캐시를 두고, 그 대가로
설정 변경이 최대 10초 늦게 반영되는 것을 받아들인다. 저장한 인스턴스는 즉시 무효화하고
나머지는 TTL만큼 뒤에 따라오는데, 대기열은 정확성 장치가 아니라 인스턴스 간에 잠깐 값이
달라도 무해하다 — 정원은 어차피 L3가 지킨다.

**DB 조회 실패 시 대기열 없음으로 떨어진다.** DB가 흔들리면 예약 자체가 이미 위태롭다.
여기서 대기열까지 막아 장애를 하나 더 얹지 않는다.

---

### Phase 8 — 커넥션풀·타임아웃·스레드 튜닝

현재 값은 **t3.micro 2대 구성에 맞춰 의도적으로 줄여둔 것**이다(`application-release.yaml` 주석).
멀티 인스턴스 + 대규모 트래픽 전제로 가려면 인스턴스 스펙과 함께 재조정해야 한다.
**값을 바꾸기 전에 Phase 9 측정이 먼저다.** 아래는 출발점이지 정답이 아니다.

| 항목 | 현재 | 제안 | 근거 |
|---|---|---|---|
| `hikari.maximum-pool-size` | 10 | `min(DB vCPU × 2 + 디스크수, 인스턴스당 20)` | 태스크 수 × 풀 크기가 MySQL `max_connections` 를 넘으면 안 된다. **태스크 수를 먼저 정하고 역산한다** |
| `hikari.minimum-idle` | 2 | `maximum-pool-size` 의 절반 | 오픈 순간 커넥션 생성 지연 회피 |
| `hikari.connection-timeout` | 3000 | 유지 | 빨리 실패하는 편이 낫다 |
| `mybatis.default-statement-timeout` | 3 | 유지 (단, 배치 쿼리는 개별 상향) | 락 대기 폭주 시 방파제 |
| `server.tomcat.threads.max` | (기본 200) | 100~150 | Phase 3 이후 스레드 2배 점유가 사라져 더 줄여도 된다. **스레드 수 > DB 커넥션 수 × 3 이면 대기만 쌓인다** |
| `server.tomcat.accept-count` | (기본 100) | 200 | 순간 유입 흡수 |
| `lettuce.pool.max-active` | 8 | 32 | Redis 사용처가 헬스체크뿐 → L1/L2/분산락으로 급증 |
| `lettuce.pool.min-idle` | 0 | 8 | 오픈 순간 커넥션 생성 지연 회피 |
| `spring.data.redis.timeout` | 3s | 1s | fail-open 이 목적이므로 **빨리 포기**해야 한다. 3초를 기다리면 Redis 장애가 곧 앱 장애다 |

**Redis timeout 1초는 중요한 변경이다.** L2/L1이 전부 fail-open 설계이므로, Redis가 느려졌을 때
빠르게 포기하고 DB로 넘어가야 의미가 있다. 3초를 기다리면 fail-open이 무용지물이다.

**ECS 오토스케일링**: `docs/aws-ecs-deployment-plan.md` 의 구성에 목표 추적 정책을 추가한다.
- CPU 60% 또는 ALB `RequestCountPerTarget` 기준
- **DB 커넥션 총량 = 태스크 수 × `maximum-pool-size` 를 반드시 상한과 함께 계산한다.**
  오토스케일링이 DB를 죽이는 게 이 구조의 전형적인 사고다.

---

### Phase 9 — 부하 테스트 검증

#### 9-1. 시나리오

| 시나리오 | 부하 | 통과 기준 |
|---|---|---|
| **S1 오픈 러시** | 30초간 VU 0→5,000 급증, 정원 1,000 | 성공 정확히 1,000, 나머지 전부 `SOLD_OUT`(5xx 0건), p99 < 1s |
| **S2 잔여 조회 폴링** | 10,000 VU가 2초 간격 폴링 | p99 < 200ms, DB QPS 증가 없음(L2 히트) |
| **S3 결제 승인** | 500 TPS, 토스 목 서버(응답 300ms) | p99 < 2s, 예약 확정 누락 0건 |
| **S4 대기열** | 50,000 VU 동시 진입 | 백엔드 유입이 `activeLimit` 근처로 유지, 순번 단조 감소 |
| **S5 Redis 장애** | S1 중 Redis 강제 종료 | 예약 계속 성공, 성공 수 여전히 정확히 1,000 |
| **S6 DB 페일오버** | S1 중 DB 순단 5초 | 5xx는 나되 복구 후 자동 회복, 정원 초과 0 |

#### 9-2. 반드시 확인할 것

- **INV-1 검증은 부하 테스트 후 SQL로 직접 한다.**
  `SELECT capacity, reserved_count, (SELECT COUNT(*) FROM reservations ...) FROM fair_dates`
  셋이 전부 일치해야 한다. 애플리케이션 응답만 보면 안 된다.
- S5(Redis 장애)는 **반드시 통과해야 하는 시나리오**다. 여기서 예약이 막히면 L2 설계가 틀린 것이다.
- 각 Phase 배포 후 S1을 돌려 기준선 대비 개선폭을 기록한다.

---

## 4. 불변식 × Phase 교차 검증표

| | Phase 2 | Phase 4 | Phase 6 | Phase 7 |
|---|---|---|---|---|
| **INV-1** 정원 초과 금지 | `occupy` 조건부 UPDATE로 이전 | 만료 복구 시 재점유 필요 | L2는 무관(과다허용 허용) | 무관 |
| **INV-2** 1인 1매 | `UK_RESERVATION_ACTIVE_USER_FAIR` 유지 | - | 롤백 시 L2 반납 필요 | 토큰↔userId 검증 |
| **INV-3** 승인 1회 | - | `markProcessing` CAS 유지 | - | - |
| **INV-4** 결제→예약 확정 | - | **보정 배치로 최초 보장** | - | - |

---

## 5. 장애 시나리오 대응표

| 장애 | 영향 | 대응 | 정합성 |
|---|---|---|---|
| Redis 전체 장애 | 대기열·좌석캐시·분산락 무력화 | 전부 fail-open, DB가 전부 처리 | **유지** |
| Redis 부분 지연 | 응답 지연 | timeout 1s로 빠르게 포기 | 유지 |
| DB 커넥션 고갈 | 예약 5xx | 대기열 `activeLimit` 하향으로 유입 조절 | 유지 |
| 토스 5xx | 결제 `PROCESSING` 잔류 | (기존 한계) 상태조회 기반 정산 — **후속 과제** | 미보장 |
| 앱 인스턴스 크래시 | 진행 중 트랜잭션 롤백 | DB 롤백 + L2는 TTL 후 재시드 | 유지 |
| 통지 유실 | 예약 확정 누락 | 보정 배치가 30초 내 재시도 | **Phase 4에서 확보** |

---

## 6. 롤아웃 순서

```
배포 A: V14 마이그레이션만 (코드 변경 없음)
   ↓ 검증: reserved_count 백필값이 실제 COUNT와 일치하는지 SQL 확인
배포 B: Phase 2 + 3 + 4 + 5 (플래그 없음, 되돌리려면 코드 롤백)
   ↓ 검증: S1/S3 부하 테스트, 예약 확정 누락 0건
배포 C: Phase 6 (seat-inventory.enabled=false 로 배포 → 확인 후 true)
   ↓ 검증: S1/S2/S5
배포 D: Phase 8 튜닝값 적용
   ↓ 검증: S1 재측정
배포 E: Phase 7 (waiting-room 은 특정 행사만 켬)
   ↓ 검증: S4
```

배포 C·E는 **플래그를 끈 상태로 먼저 올리고 나중에 켠다.** 켜는 것과 배포하는 것을
분리해야 문제 발생 시 롤백이 재배포가 아니라 환경변수 변경으로 끝난다.

---

## 7. 변경 파일 목록 (예상)

### 신규
```
src/main/resources/db/migration/V22__reservation_capacity_counter.sql
src/main/java/com/ms/petopia/api/reservation/
  dto/FairDateCapacityRow.java
  mapper/ReservationCapacityMapper.java
  service/SeatInventoryStore.java
  service/WaitingRoomService.java
  controller/WaitingRoomController.java
  dto/WaitingTicketResponse.java
src/main/resources/mapper/reservation/ReservationCapacityMapper.xml
src/main/java/com/ms/petopia/api/payment/
  client/ReservationPaymentContract.java
  client/InProcessReservationPaymentContract.java
  service/PaymentCompletionRecoveryJob.java
  mapper/ (보정 조회 추가)
src/main/java/com/ms/petopia/global/
  lock/RedisDistributedLock.java
  web/WaitingRoomInterceptor.java
  config/AsyncExecutorConfig.java
loadtest/*.js
```

### 수정
```
build.gradle                                  (actuator, micrometer-prometheus)
src/main/resources/application*.yaml          (Phase 8 튜닝값 + 신규 프로퍼티)
api/reservation/service/ReservationService.java
api/reservation/service/ReservationCancellationService.java
api/reservation/service/ReservationExpirationService.java
api/reservation/service/ReservationVisitDateChangeService.java
api/reservation/service/ReservationPaymentCompletionService.java   ★만료 복구 로직
api/reservation/service/ReservationAvailabilityService.java
api/reservation/mapper/ReservationMapper.java + .xml
api/reservation/mapper/ReservationChangeMapper.java + .xml
api/reservation/mapper/ReservationExpirationMapper.java + .xml
api/reservation/dto/ExpiringReservationRow.java                    (visitDate 추가)
api/payment/service/PaymentService.java                            ★통지 비동기화
api/payment/client/ReservationPaymentContractClient.java           (→ Rest 구현으로 리네임)
global/exception/ErrorCode.java                                    (신규 에러코드)
frontend/src/api/reservation.ts, payment.ts                        (대기열 헤더)
frontend/src/pages/reservation/*                                   (대기 화면)
frontend/src/pages/payment/PaymentResultPage.tsx                   (확정 폴링)
```

---

## 8. 이 계획이 다루지 않는 것 (후속 과제)

| 항목 | 이유 |
|---|---|
| 토스 5xx 후 `PROCESSING` 잔류 건 정산 | 토스 결제 상태조회 API 연동이 선행되어야 함. 별도 과제 |
| 만료 후 정원 소진된 결제 건 **자동 환불** | 환불 도메인 계약 확정 필요. 지금은 수동 처리 대상으로 표시만 |
| 예약금 취소 시 환불 연동 | `ReservationCancellationService` 에 이미 `TODO` 로 존재. 별도 과제 |
| DB 읽기 복제본 분리 | 조회 부하가 Phase 2·6으로 크게 줄어들어 우선순위 낮음. 재측정 후 판단 |
| 예약 생성 완전 비동기화(큐 기반) | 대기열로 유입을 조절하면 동기 처리로 충분할 것으로 판단. S1/S4 결과 보고 재검토 |
| 행사별 `activeLimit` 관리 UI | 우선 프로퍼티/환경변수로 운영 |

---

## 9. 다음 액션

1. Phase 0 착수 — actuator/메트릭 붙이고 **현재 기준선부터 측정한다.**
   개선 전 숫자가 없으면 이 작업 전체가 "느낌상 빨라짐"으로 끝난다.
2. 기준선 확보 후 Phase 1(V14) 단독 배포.
3. Phase 2~5를 한 배포로 묶어 진행.

착수 순서나 Phase 범위 조정이 필요하면 이 문서를 먼저 고치고 코드로 간다.
