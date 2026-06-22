# 류호정 트러블슈팅

한끼팟의 매칭 동시성, 트랜잭션, soft delete, 결제 SDK, 테스트 환경, Kafka 및 Docker 운영 과정에서 발생한 문제와 해결 과정을 정리한 문서입니다.

<a id="크리티컬@version낙관적-락-+-requires_new-=-버전-충돌"></a>

<details>
<summary><strong>(크리티컬)@Version(낙관적 락) + REQUIRES_NEW = 버전 충돌</strong></summary>

## (크리티컬)@Version(낙관적 락) + REQUIRES_NEW = 버전 충돌

> 브랜치: `feat/post-status-concurrency-test`

## 1. 발생 배경

self-invocation 문제를 해결하기 위해 `MatchTransactionService.createMatchInTransaction()`을 `@Transactional(propagation = Propagation.REQUIRES_NEW)`로 분리했다. `REQUIRES_NEW`는 기존 트랜잭션과 무관하게 항상 새로운 독립 트랜잭션을 시작한다는 의미인데, 이 독립성이 `@Version` 기반 낙관적 락과 만나면서 새로운 문제를 만들었다.

## 2. 증상 / 재현

`Post` 엔티티에 `@Version`이 적용되어 있는 상태에서, 동시에 여러 스레드가 `MatchTransactionService.createMatchInTransaction()`을 호출하면 `OptimisticLockException`이 빈번하게 발생했다. 분명 락 단계(Redis 분산 락)에서 동시 접근을 직렬화했는데도 DB 커밋 시점에 버전 충돌이 터졌다.

```java
@Entity
public class Post {
    @Version
    private Long version;
}
```

## 3. 원인 분석

`REQUIRES_NEW`로 시작된 각 트랜잭션은 서로 완전히 독립적이다. 트랜잭션 A가 `Post`를 읽어서(`version=5`) 메모리에 들고 있는 동안, Redis 락이 풀리고 트랜잭션 B가 들어와서 같은 `Post`를 다시 읽고(`version=5`) 수정한 뒤 커밋해 `version=6`이 된다. 이제 트랜잭션 A가 자신이 들고 있던 `version=5` 기준으로 커밋을 시도하면 DB의 현재 버전(6)과 불일치하여 `OptimisticLockException`이 발생한다.

## 4. 해결 — 비관적 락으로 전환

낙관적 락 대신 비관적 락(`PESSIMISTIC_WRITE`)을 적용해서, 트랜잭션이 `Post` 행을 읽는 순간 DB 차원에서 다른 트랜잭션의 접근을 막도록 변경했다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Post p WHERE p.id = :postId")
Optional<Post> findByIdWithLock(@Param("postId") Long postId);
```

## 5. 검증

`MatchConcurrencyTest` 전략 B로 10개 스레드 동시 요청을 보냈을 때, 정확히 1명만 성공하고 나머지 9명은 매칭 불가 예외로 정상 처리되었다. `OptimisticLockException`은 더 이상 발생하지 않았다.

## 6. 핵심 개념 & 학습 포인트

- 동시 신청처럼 충돌이 빈번한 시나리오는 비관적 락이 더 적합하다.
- `REQUIRES_NEW`는 독립 트랜잭션을 만들기 때문에 같은 데이터 접근 충돌 가능성도 함께 고려해야 한다.
- `SELECT FOR UPDATE`는 행 단위 배타 락으로 동시 수정을 사전에 막는다.

</details>

---

<a id="크리티컬self-invocation으로-@transactional-무시-select-for-update-실패"></a>

<details>
<summary><strong>(크리티컬)Self-invocation으로 @Transactional 무시 → SELECT FOR UPDATE 실패</strong></summary>

## (크리티컬)Self-invocation으로 @Transactional 무시 → SELECT FOR UPDATE 실패

> 브랜치: `refactor/match-create-service-separation`

## 1. 발생 배경

매칭 신청은 동시에 여러 명이 같은 게시글에 신청할 수 있는 선착순 API다. 처음에는 `MatchServiceImpl` 안에 락 관리 로직과 DB 트랜잭션 로직을 함께 두고, 같은 클래스의 `createMatchInTransaction()`을 `this.createMatchInTransaction()` 형태로 호출했다.

```java
@Service
@Transactional(readOnly = true)
public class MatchServiceImpl {
    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {
        return this.createMatchInTransaction(postId, applicantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateMatchResponseDto createMatchInTransaction(Long postId, Long applicantId) {
        Post post = postRepository.findByIdWithLock(postId);
    }
}
```

## 2. 증상 / 재현

동시 신청 테스트에서 `SELECT FOR UPDATE`가 걸리지 않은 것처럼 동작했다. 여러 스레드가 동시에 같은 Post를 읽고 수정하거나, `readOnly=true` 트랜잭션 안에서 쓰기 작업이 시도되어 예외가 발생했다.

## 3. 원인 분석

Spring의 `@Transactional`은 AOP 프록시 기반으로 동작한다. 같은 클래스 내부에서 `this.메서드()`로 호출하면 프록시를 거치지 않으므로 `@Transactional(REQUIRES_NEW)`가 무시된다.

## 4. 해결 — 3-클래스 분리

`MatchCreateService`는 Redis 락 관리를 담당하고, `MatchTransactionService`는 트랜잭션 내부 DB 작업을 담당하도록 분리했다.

```java
@Service
@RequiredArgsConstructor
public class MatchCreateService {
    private final MatchTransactionService matchTransactionService;

    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {
        return matchTransactionService.createMatchInTransaction(postId, applicantId);
    }
}

@Service
@RequiredArgsConstructor
public class MatchTransactionService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateMatchResponseDto createMatchInTransaction(Long postId, Long applicantId) {
        Post post = postInternalService.getPostWithPessimisticLock(postId);
    }
}
```

## 5. 검증

분리 이후 `assertThat(successCount.get()).isEqualTo(1)` 검증이 안정적으로 통과했다.

## 6. 핵심 개념 & 학습 포인트

- 같은 클래스 내부 호출은 Spring AOP 프록시를 우회한다.
- `@Transactional`, `@Async`, `@Cacheable` 모두 self-invocation 문제를 겪을 수 있다.
- 락 관리, 트랜잭션 경계, DB 작업을 분리하면 문제가 구조적으로 줄어든다.

</details>

---

<a id="크리티컬redis-락-점유-중-kafka-발송-그룹-매칭-스레드-전부-타임아웃"></a>

<details>
<summary><strong>(크리티컬)Redis 락 점유 중 Kafka 발송 → 그룹 매칭 스레드 전부 타임아웃</strong></summary>

## (크리티컬)Redis 락 점유 중 Kafka 발송 → 그룹 매칭 스레드 전부 타임아웃

> 브랜치: `feat/post-status-concurrency-test`

## 1. 발생 배경

그룹 매칭 신청 성공 후 Kafka 알림을 발송하도록 구현했는데, 처음에는 알림 발송 코드를 트랜잭션 및 Redis 락 점유 구간 내부에서 동기로 호출했다.

```java
Post post = postInternalService.getPostWithPessimisticLock(postId);
notificationPublisher.send(...);
```

## 2. 증상 / 재현

그룹 매칭 동시성 테스트에서 신청자가 늘어날수록 나머지 스레드들이 점점 느려지다가 타임아웃으로 실패했다.

## 3. 원인 분석

Kafka 전송은 네트워크 I/O라서 지연될 수 있는데, 이 시간이 그대로 Redis 락 점유 시간에 더해졌다. 그룹 매칭은 신청자가 순서대로 락을 획득해야 하므로 한 요청의 Kafka 지연이 뒤 요청 전체의 대기 시간으로 누적되었다.

## 4. 해결 — 알림 발송을 락 해제 후 비동기로 이동

DB 트랜잭션과 알림 발송을 분리하고, 알림은 락 해제 이후 `@Async`로 처리하도록 변경했다.

```java
@Async
public void sendMatchApplied(Long userId, Long matchId) {
    kafkaTemplate.send(KafkaTopics.NOTIFICATIONS, ...);
}
```

## 5. 검증

그룹 매칭 동시성 테스트 재실행 시 평균 처리 시간이 안정화되었고, 타임아웃 실패가 사라졌다.

## 6. 핵심 개념 & 학습 포인트

- 락 안에는 최소 임계 영역만 둔다.
- Kafka, 외부 API, 파일 I/O 같은 블로킹 작업은 락 내부에 두지 않는다.
- 부가 작업은 핵심 상태 변경 이후 비동기로 분리하는 것이 안전하다.

</details>

---

<a id="크리티컬@sqlrestriction-join-함정-soft-delete-필터가-join까지-전파"></a>

<details>
<summary><strong>(크리티컬)@SQLRestriction JOIN 함정 — soft delete 필터가 JOIN까지 전파</strong></summary>

## (크리티컬)@SQLRestriction JOIN 함정 — soft delete 필터가 JOIN까지 전파

> 브랜치: `refactor/soft-delete`

## 1. 발생 배경

게시글 삭제는 실제 행을 지우지 않고 `deleted_at` 컬럼을 기록하는 soft delete 방식으로 구현했다. Hibernate의 `@SQLRestriction`으로 삭제되지 않은 게시글만 조회되도록 했다.

```java
@Entity
@SQLRestriction("deleted_at IS NULL")
public class Post extends SoftDeleteEntity { ... }
```

## 2. 증상 / 재현

사용자가 자신의 매칭 내역을 조회할 때, 연결된 게시글이 soft delete된 경우 매칭 자체가 결과에서 사라졌다.

## 3. 원인 분석

`@SQLRestriction`은 엔티티 단위 전역 필터라 직접 조회뿐 아니라 JOIN 대상이 될 때도 조건이 붙는다. 삭제된 게시글을 숨기려던 의도가 삭제된 게시글과 연결된 매칭 이력까지 숨기는 결과가 되었다.

## 4. 해결

매칭 목록처럼 JOIN이 필요한 조회는 QueryDSL에서 조건을 명시적으로 제어하고, 삭제된 게시글 정보가 필요한 경우 native query로 우회했다.

```java
@Query(value = "SELECT * FROM posts WHERE post_id = :postId", nativeQuery = true)
Optional<Post> findByIdIncludingDeleted(@Param("postId") Long postId);
```

## 5. 검증

게시글 삭제 후에도 해당 게시글에 연결된 매칭 내역이 정상 조회되는 것을 확인했다.

## 6. 핵심 개념 & 학습 포인트

- 엔티티 전역 필터는 JOIN에도 전파된다.
- 이력성 데이터는 원본 삭제 이후에도 독립적으로 조회되어야 하는지 먼저 검토해야 한다.
- 완전 우회가 필요하면 native query가 확실하다.

</details>

---

<a id="크리티컬-동시성portone-sdk-kotlin-npe-apibase에-null-전달"></a>

<details>
<summary><strong>(크리티컬, 동시성)PortOne SDK Kotlin NPE — apiBase에 null 전달</strong></summary>

## (크리티컬, 동시성)PortOne SDK Kotlin NPE — apiBase에 null 전달

> 브랜치: `feature/payment-create`

## 1. 발생 배경

PortOne V2 Java SDK는 Kotlin으로 작성되어 있다. `PaymentClient` 객체 생성 시 두 번째 인자인 `apiBase`를 임시로 `null`로 넘겼다.

```java
return new PaymentClient(
        properties.apiSecret(),
        null,
        properties.storeId()
);
```

## 2. 증상 / 재현

컴파일은 통과했지만 결제 준비 API 호출 시 PortOne SDK 내부에서 `NullPointerException`이 발생했다.

## 3. 원인 분석

Kotlin의 non-null 파라미터는 Java 컴파일러가 막아주지 않는다. 대신 Kotlin 런타임의 null 체크가 메서드 진입 시점에 NPE를 던진다.

## 4. 해결

`apiBase`에 실제 PortOne API 엔드포인트를 전달했다.

```java
return new PaymentClient(
        properties.apiSecret(),
        "https://api.portone.io",
        properties.storeId()
);
```

## 5. 검증

`PaymentClient` 빈 생성, 결제 준비, 결제 검증 API가 모두 정상 동작했다.

## 6. 핵심 개념 & 학습 포인트

- Kotlin 라이브러리를 Java에서 호출할 때 non-null 계약을 직접 지켜야 한다.
- 외부 SDK는 파라미터가 필수인지 문서와 소스를 먼저 확인해야 한다.

</details>

---

<a id="동시성catch-matchexception-|-runtimeexception-컴파일-에러"></a>

<details>
<summary><strong>(동시성)catch (MatchException | RuntimeException) 컴파일 에러</strong></summary>

## (동시성)catch (MatchException | RuntimeException) 컴파일 에러

> 브랜치: `feat/match-concurrency`

## 1. 발생 배경

매칭 동시성 테스트에서 `MatchException`과 일반 런타임 예외를 모두 잡기 위해 멀티 캐치를 작성했다.

```java
catch (MatchException | RuntimeException e) { ... }
```

## 2. 증상 / 재현

컴파일러가 "Types in multi-catch must be disjoint" 에러를 표시했다.

## 3. 원인 분석

`MatchException`은 `RuntimeException`을 상속한다. 멀티 캐치에 나열된 타입들은 서로 상속 관계가 없어야 하므로 컴파일 에러가 발생했다.

## 4. 해결

상위 타입인 `RuntimeException` 하나만 catch하도록 단순화했다. 더 세분화된 통계가 필요하면 구체적인 예외를 먼저 catch하고 나머지를 상위 타입으로 묶었다.

## 5. 검증

수정 후 컴파일과 테스트가 정상 통과했다.

## 6. 핵심 개념 & 학습 포인트

- 멀티 캐치는 상속 관계가 아닌 타입끼리만 사용할 수 있다.
- catch 블록은 구체적인 예외를 먼저, 일반적인 예외를 나중에 둔다.

</details>

---

<a id="동시성interruptedexception-in-lambda-checked-exception-처리"></a>

<details>
<summary><strong>(동시성)InterruptedException in lambda — checked exception 처리</strong></summary>

## (동시성)InterruptedException in lambda — checked exception 처리

> 브랜치: `feat/match-concurrency`

## 1. 발생 배경

동시성 테스트에서 여러 스레드를 동시에 출발시키기 위해 `CountDownLatch.await()`를 람다 내부에서 호출했다.

```java
executor.submit(() -> {
    startLatch.await();
    matchConcurrencyService.applyMatch(postId, applicantId);
});
```

## 2. 증상 / 재현

`Unhandled exception: InterruptedException` 컴파일 에러가 발생했다.

## 3. 원인 분석

`Runnable.run()`은 checked exception을 던질 수 없지만, `CountDownLatch.await()`는 `InterruptedException`을 던질 수 있다.

## 4. 해결

람다 내부에서 `try-catch`로 처리하고, `Thread.currentThread().interrupt()`로 인터럽트 상태를 복구했다.

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

## 5. 검증

동시성 테스트가 정상 컴파일 및 실행되었다.

## 6. 핵심 개념 & 학습 포인트

- Runnable 람다 내부의 checked exception은 직접 처리해야 한다.
- `InterruptedException`을 삼키지 말고 인터럽트 플래그를 복구해야 한다.

</details>

---

<a id="테스트testredismockconfig-mock-redis가-실제-빈을-덮어쓰지-못하고-역으로-당함"></a>

<details>
<summary><strong>(테스트)TestRedisMockConfig — Mock Redis가 실제 빈을 덮어쓰지 못하고 역으로 당함</strong></summary>

## (테스트)TestRedisMockConfig — Mock Redis가 실제 빈을 덮어쓰지 못하고 역으로 당함

> 브랜치: `feat/post-status-concurrency-test`

## 1. 발생 배경

Redis 컨테이너 없이 테스트를 돌리기 위해 `TestRedisMockConfig`를 만들고 `RedissonClient`, `StringRedisTemplate`을 Mockito로 모킹했다.

## 2. 증상 / 재현

`@Primary`를 붙였지만 테스트 실행 시 실제 Docker Redis에 연결하려는 시도가 발생했다. `allow-bean-definition-overriding: true` 설정도 효과가 없었다.

## 3. 원인 분석

`@Primary`는 주입 시점의 선택 우선순위이고, 빈 정의 덮어쓰기는 등록 시점의 교체다. 실제 `RedisConfig`가 나중에 스캔되면 Mock 빈이 덮일 수 있다.

## 4. 해결

Mock으로 분산 락 통합 테스트를 대체하지 않고, 실제 Docker Redis를 사용하도록 변경했다.

## 5. 검증

실제 Redis 대상으로 `MatchConcurrencyTest`의 Redis 전략을 실행해 동시 요청 중 정확히 1명만 성공하는 것을 확인했다.

## 6. 핵심 개념 & 학습 포인트

- `@Primary`와 빈 정의 덮어쓰기는 별개다.
- 분산 락처럼 원자성 검증이 핵심인 컴포넌트는 Mock보다 실제 인스턴스 테스트가 안전하다.

</details>

---

<a id="테스트@aftereach-+-@transactional-충돌-테스트-데이터-미삭제"></a>

<details>
<summary><strong>(테스트)@AfterEach + @Transactional 충돌 — 테스트 데이터 미삭제</strong></summary>

## (테스트)@AfterEach + @Transactional 충돌 — 테스트 데이터 미삭제

> 브랜치: `feat/match-concurrency`

## 1. 발생 배경

동시성 테스트 후 `@AfterEach`와 `@Transactional`로 테스트 데이터를 정리하려고 했다.

## 2. 증상 / 재현

이전 테스트에서 생성된 `Match`, `Post` row가 DB에 남아 다음 테스트에 영향을 주었다.

## 3. 원인 분석

`@Transactional` 자동 롤백은 메인 테스트 스레드의 트랜잭션에만 적용된다. `ExecutorService` 자식 스레드에서 발생한 DB 변경은 별도 트랜잭션에서 이미 커밋되므로 롤백 대상이 아니다.

## 4. 해결

`PlatformTransactionManager`로 수동 트랜잭션을 열어 명시적으로 삭제하고 커밋했다.

```java
TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
try {
    matchRepository.deleteAllInBatch();
    entityManager.createNativeQuery("DELETE FROM posts").executeUpdate();
    userRepository.deleteAllInBatch();
    transactionManager.commit(status);
} catch (Exception e) {
    transactionManager.rollback(status);
}
```

## 5. 검증

연속 테스트 실행 시 매 테스트가 깨끗한 DB 상태에서 시작되는 것을 확인했다.

## 6. 핵심 개념 & 학습 포인트

- Spring 트랜잭션은 기본적으로 스레드 로컬 기반이다.
- 멀티스레드 테스트 정리에는 수동 트랜잭션 제어가 유용하다.

</details>

---

<a id="아키텍쳐partialrefundpoint-이중-50%-버그-내부-구현-미확인"></a>

<details>
<summary><strong>(아키텍쳐)partialRefundPoint 이중 50% 버그 — 내부 구현 미확인</strong></summary>

## (아키텍쳐)partialRefundPoint 이중 50% 버그 — 내부 구현 미확인

> 브랜치: `feat/cancel-match`

## 1. 발생 배경

매칭 취소 시 취소자의 예치금을 50% 환급해야 했다. `UserPointService.partialRefundPoint()`가 이미 있었지만, 호출부에서 다시 절반을 계산했다.

## 2. 증상 / 재현

정책상 50% 환급이어야 하는데 실제로는 예치금의 25%만 환급되었다.

## 3. 원인 분석

`partialRefundPoint()` 내부에서 이미 `amount / 2`를 계산하는데 호출부에서도 절반을 계산한 값을 넘겼다.

## 4. 해결

호출부에서는 원금 그대로를 전달하도록 변경했다.

```java
userPointService.partialRefundApplicantDeposit(
        userId,
        match.getApplicantDeposit(),
        matchId
);
```

## 5. 검증

포인트 거래 내역에서 예치금의 정확히 50%가 `PARTIAL_REFUND` 타입으로 기록되는 것을 확인했다.

## 6. 핵심 개념 & 학습 포인트

- 메서드 계약은 시그니처만으로 파악할 수 없다.
- 계산 책임은 한 곳에만 둬야 한다.
- 인터페이스에는 파라미터 의미를 문서화하는 것이 좋다.

</details>

---

<a id="아키텍쳐host-취소-시-알림-중복-발송-버그"></a>

<details>
<summary><strong>(아키텍쳐)HOST 취소 시 알림 중복 발송 버그</strong></summary>

## (아키텍쳐)HOST 취소 시 알림 중복 발송 버그

> 브랜치: `feat/cancel-match`

## 1. 발생 배경

HOST가 매칭을 취소하면 모든 GUEST에게 환불과 알림을 보내야 한다. 그룹 매칭은 여러 GUEST를 `for`문으로 순회한다.

## 2. 증상 / 재현

HOST 취소 시 GUEST가 알림을 두 번 이상 받았다.

## 3. 원인 분석

`for`문 안에서 이미 각 GUEST에게 알림을 보냈는데, `for`문 바깥에 조건 없는 알림 발송 코드가 남아 있었다.

## 4. 해결

`for`문 바깥의 중복 알림 발송 코드를 제거하고, HOST 취소 분기 안에서 각 GUEST에게 한 번씩만 알림이 가도록 정리했다.

## 5. 검증

1:1과 1:N 그룹 매칭 모두에서 각 GUEST가 정확히 한 번씩만 알림을 받는 것을 SSE 로그로 확인했다.

## 6. 핵심 개념 & 학습 포인트

- 분기 복사 후 원본 분기에만 필요한 코드가 남지 않았는지 확인해야 한다.
- 알림 발송처럼 부작용 횟수가 중요한 로직은 `times(1)` 검증이 필요하다.

</details>

---

<a id="인프라kafka-group-id-누락-applicationcontext-로딩-실패"></a>

<details>
<summary><strong>(인프라)Kafka group-id 누락 — ApplicationContext 로딩 실패</strong></summary>

## (인프라)Kafka group-id 누락 — ApplicationContext 로딩 실패

> 브랜치: `feat/match-concurrency`

## 1. 발생 배경

Kafka DLQ 처리를 위해 `DlqEventConsumer`가 추가되었고, `@KafkaListener`에 `${spring.kafka.consumer.group-id}` 플레이스홀더를 사용했다.

## 2. 증상 / 재현

테스트 실행 시 `ApplicationContext` 로딩이 실패했다.

## 3. 원인 분석

메인 설정에는 `spring.kafka.consumer.group-id`가 있었지만 `application-test.yml`에는 없었다. test 프로필에서 플레이스홀더를 해석하지 못해 빈 생성이 실패했다.

## 4. 해결

`application-test.yml`에 값을 추가했다.

```yaml
spring:
  kafka:
    consumer:
      group-id: test-group
```

## 5. 검증

테스트 컨텍스트가 정상 로딩되고 `@KafkaListener` 빈이 등록되었다.

## 6. 핵심 개념 & 학습 포인트

- 외부 설정 키는 local, test, prod 등 필요한 모든 프로필에 존재해야 한다.
- 플레이스홀더 해석 실패는 컨텍스트 전체 로딩 실패로 이어질 수 있다.

</details>

---

<a id="인프라-swaggerdocker-스테일-이미지-코드-반영-안-된-컨테이너-실행"></a>

<details>
<summary><strong>(인프라, Swagger)Docker 스테일 이미지 — 코드 반영 안 된 컨테이너 실행</strong></summary>

## (인프라, Swagger)Docker 스테일 이미지 — 코드 반영 안 된 컨테이너 실행

> 브랜치: `feat/swagger`

## 1. 발생 배경

Swagger 문서화 작업 후 변경 사항 확인을 위해 `docker compose up`만 실행했다.

## 2. 증상 / 재현

코드는 수정했지만 컨테이너에서는 변경 전 코드가 실행되었다.

## 3. 원인 분석

`docker compose up`은 이미지가 이미 있으면 새로 빌드하지 않고 기존 이미지를 재사용한다. 따라서 코드 변경 사항이 컨테이너에 반영되지 않았다.

## 4. 해결

캐시를 무시하고 이미지를 다시 빌드했다.

```bash
docker compose build --no-cache app
docker compose up -d app
```

## 5. 검증

최신 Swagger 문서와 변경된 엔드포인트 동작이 반영된 것을 확인했다.

## 6. 핵심 개념 & 학습 포인트

- `docker compose up`은 기존 이미지를 재사용한다.
- `build --no-cache`는 레이어 캐시까지 무시하고 다시 빌드한다.
- CI/CD에서는 커밋 해시 기반 이미지 태그로 배포 버전을 구분하는 것이 안전하다.

</details>
