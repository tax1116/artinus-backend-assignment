# ARTINUS Subscription Backend

ARTINUS 백엔드 엔지니어 (5~8년) 과제 구현체. 구독/해지 API, 외부 API 장애 대응, 이력 + LLM 요약 조회 API 를 포함한다.

## 핵심 요약

- **언어/프레임워크**: Kotlin 2.2.21 + Spring Boot 4.0.3 (템플릿 고정)
- **아키텍처**: 경량 헥사고날 — 순수 Kotlin `domain` 모듈 + Spring `app` 모듈. 도메인이 **포트(인터페이스)** 를 소유하고, 인프라가 **어댑터**를 구현.
- **DB**: H2 인메모리 (PoC 범위). `ddl-auto=create-drop` + `data.sql` 로 채널 6건 seed.
- **HTTP 클라이언트**: Spring `RestClient` + `JdkClientHttpRequestFactory`. 리액티브 스택(`spring-boot-starter-webflux`, `resilience4j-reactor`) 의존성 없음.
- **외부 API 장애 대응**: Resilience4j 2.3 (CircuitBreaker + Retry + TimeLimiter 코어 API 직접 조합)
- **LLM**: Anthropic Messages API (`claude-haiku-4-5-20251001`), 실패 시 룰 기반 요약으로 graceful degradation

## 기술 선택 근거

| 영역 | 선택 | 이유 |
|---|---|---|
| 언어 | Kotlin 21 | 템플릿 기본. null 안전성 / value class 로 `PhoneNumber` 같은 도메인 타입을 자연스럽게 표현. |
| 프레임워크 | Spring Boot 4 | 템플릿 기본. Spring MVC + Data JPA + Actuator. |
| 영속성 | Spring Data JPA + Hibernate | 엔티티 3개 수준이라 JPA 가 ROI 가 높다. 감사 로그(`subscription_history`) 도 쓰기/조회 패턴이 단순해 JOOQ 까지 갈 필요 없음. |
| 스키마 관리 | `ddl-auto=create-drop` + `data.sql` | PoC 전제. 운영에서는 Flyway + RDS MySQL 로 전환할 것을 가정하지만 구현 범위 밖. |
| HTTP 클라이언트 | `RestClient` + `JdkClientHttpRequestFactory` | 동기 호출을 그대로 표현하면서도 JDK 내장 `HttpClient` 로 연결/읽기 타임아웃을 세밀 제어. `WebClient.block()` 안티패턴 회피. |
| 장애 대응 | Resilience4j 2.3 코어 API | CircuitBreaker + Retry + TimeLimiter 를 프로그래매틱으로 조합. Spring Boot 4 와 starter 호환 상황 불확실해 코어 API 직접 사용. |
| LLM | Anthropic Messages API | 모델 선택 자유 조건. `claude-haiku-4-5` 는 한국어 요약 품질 / 지연 / 비용 모두 충분. SDK 없이 `RestClient` 로 직접 호출해 런타임 의존성 최소화. |
| 테스트 | JUnit5 + MockWebServer + Kotest 단언 | 도메인 단위 테스트 + Spring 통합 테스트 + `MockWebServer` 로 csrng 재시도 / 서킷 / bypass 검증. |

## 프로젝트 구조

```
artinus-backend-assignment/
├── subscription/
│   ├── domain/                                       # 순수 Kotlin, Spring/JPA 의존 X
│   │   └── .../subscription/domain/
│   │       ├── model/                                # Member, Channel, SubscriptionHistory, PhoneNumber,
│   │       │                                         # SubscriptionStatus, ChannelType, HistoryAction, StatusTransition
│   │       ├── policy/                               # SubscriptionTransitionPolicy (상태 머신)
│   │       ├── repository/                           # 포트: MemberRepository, ChannelRepository, HistoryRepository
│   │       └── exception/                            # DomainException sealed 계열
│   └── app/                                          # Spring Boot
│       ├── main/kotlin/.../subscription/app/
│       │   ├── ArtinusSubscriptionApplication.kt
│       │   ├── application/                          # 유스케이스 (트랜잭션 경계)
│       │   │   ├── SubscriptionService.kt
│       │   │   └── port/                             # RandomDecisionPort, HistorySummarizerPort (애플리케이션 포트)
│       │   ├── infra/
│       │   │   ├── persistence/                      # JPA 엔티티 + JpaRepository + 도메인 포트 Adapter
│       │   │   │   ├── {Channel,Member,History}Entity.kt
│       │   │   │   ├── {Channel,Member,History}JpaRepository.kt
│       │   │   │   └── {Channel,Member,History}RepositoryAdapter.kt
│       │   │   ├── csrng/                            # CsrngClient, CsrngResilienceConfig, CsrngProperties
│       │   │   └── llm/                              # AnthropicSummarizer, LlmProperties
│       │   └── web/                                  # SubscriptionController, DTO 파일들, GlobalExceptionHandler (RFC7807)
│       └── main/resources/
│           ├── application.yml                       # default / test 프로파일
│           └── data.sql                              # 채널 6건 seed
```

### 헥사고날 경계

- **domain** 은 Spring / JPA 를 모른다. 포트(`MemberRepository`, `ChannelRepository`, `HistoryRepository`) 를 자기가 소유함.
- **application** 은 도메인 포트를 주입받아 유스케이스를 조립한다. 외부 시스템 의존(`RandomDecisionPort`, `HistorySummarizerPort`) 도 포트로 추상화.
- **infra/persistence** 는 JPA 엔티티 + Spring Data JPA 인터페이스 + **도메인 포트를 구현하는 Adapter** 세 층으로 나뉜다. 서비스 코드는 JPA 엔티티/리포지토리를 직접 보지 않는다.
- **테스트**는 포트 fake 로 외부 의존을 치환 → 실제 네트워크 호출 없이 유스케이스 검증.

## 실행법

### 준비

```bash
cp .env.example .env     # API 키 없어도 동작. 없으면 요약은 룰 기반 폴백으로 내려감.
```

JDK 21 필요. 나머지는 Gradle 래퍼가 처리.

### 로컬 실행 (H2)

```bash
./gradlew :subscription:app:bootRun
# 또는
ANTHROPIC_API_KEY=sk-ant-... ./gradlew :subscription:app:bootRun
```

- H2 콘솔: `http://localhost:8080/h2` (JDBC URL: `jdbc:h2:mem:artinus;MODE=LEGACY`)
- 헬스 체크: `GET http://localhost:8080/actuator/health`

> 운영 배포(RDS MySQL + Flyway) 는 PoC 구현 범위 밖.

### 빌드/테스트

```bash
./gradlew check                                 # ktlint + 전체 테스트
./gradlew :subscription:domain:test             # 도메인 단위 테스트
./gradlew :subscription:app:test                # Spring/통합 테스트
./gradlew ktlintFormat                          # 포맷 자동 수정
./gradlew :subscription:app:bootJar             # 실행 가능 jar
```

## API 명세

기본 경로: `/api/v1/subscriptions`. 모든 에러 응답은 RFC 7807 `application/problem+json`.

### 1. 구독하기 — `POST /api/v1/subscriptions`

```bash
curl -X POST http://localhost:8080/api/v1/subscriptions \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 3d4f...' \
  -d '{"phoneNumber":"010-1234-5678","channelId":1,"targetStatus":"STANDARD"}'
```

응답 (201):

```json
{
  "phoneNumber": "010-****-5678",
  "fromStatus": "NONE",
  "toStatus": "STANDARD",
  "action": "SUBSCRIBE",
  "historyId": 42
}
```

### 2. 구독 해지 — `POST /api/v1/subscriptions/cancel`

```bash
curl -X POST http://localhost:8080/api/v1/subscriptions/cancel \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"010-1234-5678","channelId":5,"targetStatus":"NONE"}'
```

### 3. 구독 이력 조회 — `GET /api/v1/subscriptions/history?phoneNumber=...`

```bash
curl 'http://localhost:8080/api/v1/subscriptions/history?phoneNumber=010-1234-5678'
```

응답:

```json
{
  "phoneNumber": "010-****-5678",
  "history": [
    { "id": 1, "channelId": 1, "channelName": "홈페이지",
      "action": "SUBSCRIBE", "fromStatus": "NONE", "toStatus": "STANDARD",
      "occurredAt": "2026-01-01T02:30:00" }
  ],
  "summary": "2026년 1월 1일 홈페이지를 통해 일반 구독으로 가입하였습니다."
}
```

LLM 호출 실패 / 미설정 시에도 200 응답 + `summary` 는 룰 기반 폴백.

### 에러 매핑 (`GlobalExceptionHandler`)

| 예외 | HTTP | Problem Type URI |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `urn:problem-type:validation-error` |
| `IllegalArgumentException` | 400 | `urn:problem-type:bad-request` |
| `ChannelNotAllowedException` | 403 | `urn:problem-type:channel-not-allowed` |
| `ChannelNotFoundException` | 404 | `urn:problem-type:channel-not-found` |
| `MemberNotFoundException` | 404 | `urn:problem-type:member-not-found` |
| `IllegalTransitionException` | 409 | `urn:problem-type:illegal-transition` |
| `ExternalDecisionRejectedException` | 422 | `urn:problem-type:external-rejected` |
| `ExternalDecisionUnavailableException` | 503 | `urn:problem-type:external-unavailable` |
| `Exception` (catch-all) | 500 | `urn:problem-type:internal-error` |

### Idempotency

`Idempotency-Key` 헤더가 있으면 `subscription_history.idempotency_key` UNIQUE 컬럼에 저장된다. 같은 키로 재요청이 오면:

1. 서비스가 먼저 `historyRepository.findByIdempotencyKey(key)` 로 조회
2. 존재하면 **csrng 재호출 없이** 기존 결과 그대로 반환
3. 존재하지 않으면 정상 흐름 진행 — 설사 동시 요청이 두 번 들어와도 DB UNIQUE 가 최종적으로 한 건만 허용

키가 없는 요청은 서버가 UUID 를 발급해 히스토리 추적은 유지하되, 클라이언트 재시도 안전성은 제공하지 않는다.

## 외부 API 장애 대응 전략

csrng 는 이 과제에서 "트랜잭션 커밋/롤백을 외부가 결정" 하는 특수한 결정자다. 대응은 4 계층:

1. **타임아웃 바운드** — 연결 2s / 읽기 3s / 전체 4s. DB 락 보유 시간 상한 역할.
2. **지수 백오프 재시도 3회** — 네트워크/5xx/빈 응답만 재시도. `random=0` 은 "정상 거절" 로 취급해 재시도하지 **않음** (`retryOnException` 필터).
3. **Circuit Breaker** — failureRate 50% 초과 시 30초 오픈. downstream 포화 방지.
4. **Idempotency Key 전파** — 재시도 시 같은 키 전송 (인터페이스 수준에서만, csrng 자체는 stateless).

### 트랜잭션 경계 설계

csrng 호출을 **트랜잭션 내부**에서 수행한다:

1. `@Transactional` 진입
2. 회원 행 `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) + `@Version` 으로 동시 변경 방지
3. 도메인 규칙 검증 (채널 subscribable / 상태 전이)
4. **csrng 호출** → `random=0` 이면 `ExternalDecisionRejectedException` → Spring 이 트랜잭션 롤백
5. 회원 상태 업데이트 + 이력 append
6. 커밋

트레이드오프:

- (+) "DB 커밋 후 외부 API 실패" 시나리오가 원천 차단. csrng 결과와 DB 상태가 강결합.
- (−) csrng 응답 대기 동안 DB 락 점유 → 같은 회원 동시 요청은 직렬화. 상한 4s 로 바운드.
- (−) "csrng 측은 처리했는데 응답이 유실" 된 경우 DB 는 롤백. csrng 가 stateless 라 지금은 문제 없지만, 결제 같은 side-effect 성 API 로 확장하려면 **Outbox 패턴** 도입이 필요하다.

### Degraded Mode

`CSRNG_BYPASS_ON_OUTAGE=true` 로 전환 가능. 서킷 오픈 / 타임아웃 지속 시 비즈니스 연속성을 우선시하기 위한 운영자 수동 스위치. 외부 실패를 "커밋" 으로 간주. 기본값 `false` (안전 우선).

## LLM 통합 설계

- **모델**: Anthropic `claude-haiku-4-5-20251001`. 한국어 요약 품질/지연/비용 모두 충분.
- **HTTP**: `RestClient` 직접 호출 (SDK 의존성 없음).
- **PII**: 프롬프트에 전화번호를 포함하지 않는다. 채널/날짜/상태 전이만 전송.
- **실패 처리**: 네트워크/API 에러/빈 응답 → `summarize()` 가 예외 → 서비스가 `runCatching` 으로 잡아 룰 기반 폴백으로 교체. history 는 항상 응답.
- **비용 상한**: `max_tokens=512`. 이력이 많은 회원은 최근 N 건만 포함하도록 확장 포인트 열려 있음 (현재는 전량 포함).
- **캐싱**: 미구현. 트래픽이 붙으면 `phoneNumber` + 최신 `history.id` 해시를 키로 하는 Redis TTL 캐시로 비용 절감 가능.

## 도메인 모델

- `Member` — 현재 구독 상태 스냅샷. 채널/전이 규칙을 내부에서 강제(`subscribe`, `cancel`). `@Version` optimistic lock 지원.
- `Channel` — seed 6건. `ChannelType` 으로 구독/해지 가능 여부를 표현.
- `SubscriptionHistory` — 감사 로그 (append-only). `idempotency_key` UNIQUE 인덱스로 재요청 안전성 보장.
- `SubscriptionTransitionPolicy` — 상태 머신. 선언형 맵으로 정의, 전이 불가는 `IllegalTransitionException`.

## 보안

- **API 키**: 환경변수만 허용. `application.yml` 하드코딩 X. `.env` 는 `.gitignore`.
- **PII**: 전화번호 응답 마스킹 (`010-****-5678`), LLM 프롬프트에 미포함. 컬럼 레벨 암호화/토큰화는 운영 시 별도 설계.
- **입력 검증**: Bean Validation + `PhoneNumber` 값 객체 정규식 검증. JPA 로 SQL Injection 방어.

## 테스트 커버리지

| 대상 | 위치 |
|---|---|
| 상태 전이 규칙 (구독/해지 전이 매트릭스) | `domain` : `SubscriptionTransitionPolicyTest` |
| 채널 권한 규칙 | `domain` : `ChannelTest` |
| 전화번호 값 객체 정규화/마스킹 | `domain` : `PhoneNumberTest` |
| 유스케이스 — 정상 구독 / csrng=0 롤백 / csrng 장애 전파 / 채널·전이 예외 / 멱등성 replay | `app` : `SubscriptionServiceTest` |
| csrng 클라이언트 — 재시도 / random=0 / bypass | `app` : `CsrngClientTest` (MockWebServer) |

외부 네트워크 호출은 테스트에서 **전혀** 발생하지 않는다.

## 남은 과제 / 개선 여지

- **Outbox 패턴** — csrng 가 side-effect 성 API (결제 등) 로 교체되면 트랜잭션 내부 호출에서 outbox + SQS 로 전환 필요.
- **분산 멱등성 스토어** — 현재는 DB UNIQUE. 트래픽 증가 시 Redis `SETNX` 로 락/캐시/멱등성 통합.
- **관측성** — Micrometer 메트릭 이름 정의. 대시보드/알람 프로비저닝은 별도 과제.
- **LLM 응답 캐시** — 동일 이력 반복 요청 TTL 캐시로 비용/지연 절감.
- **이벤트 발행** — 상태 변경 이벤트 SNS publish → 마케팅/알림 연계.
- **Bedrock 전환** — 운영 시 PrivateLink 로 망 경계 안에서 모델 호출.
