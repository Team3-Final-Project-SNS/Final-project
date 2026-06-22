# 박수지 트러블슈팅

한끼팟의 Kafka 기반 알림 처리, Consumer 재시도, DLT, Redis 멱등성 처리 과정에서 발생한 문제와 해결 과정을 정리한 문서입니다.

## Kafka 알림 Consumer 예외 삼킴으로 재시도/DLT 미동작

## 배경

Kafka 기반 알림 처리 구조에서는 Consumer 처리 중 예외가 발생하면 Kafka가 재시도하고, 최종 실패 시 DLT로 이동하도록 설계되어 있었다.

하지만 알림 Consumer의 실패 처리 흐름을 점검하던 중, 실제로는 재시도와 DLT가 정상 동작하지 않을 수 있는 문제를 발견했다.

---

## 문제 상황

알림 이벤트는 다음 흐름으로 처리된다.

```text
도메인 서비스
 → NotificationPublisher
 → Kafka notifications Topic
 → NotificationEventConsumer
 → Notification DB 저장
 → SSE 전송
```

프로젝트에는 이미 다음 장애 대응 구조가 있었다.

```text
Consumer 처리 실패
 → DefaultErrorHandler
 → 1초 간격 3회 재시도
 → 최종 실패 시 notifications.DLT 발행
```

하지만 Consumer 내부에서 예외를 처리한 뒤 Kafka까지 실패를 전달하지 않으면, Kafka는 메시지가 정상 처리된 것으로 판단한다.

결과적으로 알림 저장 실패가 발생해도 재시도와 DLT가 동작하지 않아 알림이 유실될 수 있었다.

---

## 원인 분석

Kafka의 재시도와 DLT는 Consumer 메서드 밖으로 예외가 전달되어야 동작한다.

기존 구조에서는 Consumer 처리 중 실패가 발생하더라도 예외가 Kafka ErrorHandler까지 전달되지 않으면 다음 흐름이 발생한다.

```text
Consumer 처리 실패
 → 예외 발생
 → Consumer 내부에서 처리 종료
 → Kafka는 정상 처리로 판단
 → ACK 처리
 → 재시도 없음
 → DLT 이동 없음
```

또 다른 문제도 있었다.

알림 Consumer는 중복 처리를 막기 위해 Redis 기반 멱등성 검사를 사용하고 있었다.

```java
kafkaIdempotencyService.isFirstProcessing(eventId)
```

기존 `KafkaIdempotencyService`는 처리 시작 시 `eventId`를 Redis에 저장했다. 하지만 처리 실패 시 저장된 `eventId`를 삭제하는 로직은 없었다.

```text
기존에는 Redis SET NX 방식으로 eventId를 저장해 중복 처리를 방지했지만,
처리 실패 시 eventId를 제거하는 markFailed() 로직은 존재하지 않았다.
```

이 상태에서 단순히 예외만 다시 던지면 다음 문제가 발생한다.

```text
1차 처리 시작
 → eventId Redis 저장
 → Notification DB 저장 실패
 → 예외 발생
 → Kafka 재시도
 → 동일 eventId 수신
 → Redis에 이미 eventId 존재
 → 중복 메시지로 판단
 → 재처리 skip
```

즉, 재시도 구조를 살리려면 **예외 재전파**뿐만 아니라 **실패한 eventId의 멱등성 키 삭제**도 함께 필요했다.

---

## 해결 방법

## 1. 실패 시 Redis 멱등성 키 삭제 메서드 추가

`KafkaIdempotencyService`에 `markFailed()` 메서드를 추가했다.

```java
public void markFailed(String eventId) {
    String key = KEY_PREFIX + eventId;
    redisTemplate.delete(key);
    log.warn("[Kafka 멱등성] 처리 실패 - 멱등성 키 삭제 - eventId: {}", eventId);
}
```

이 메서드는 Consumer 처리 실패 시 Redis에 저장된 `eventId`를 삭제한다.

```text
처리 실패 시 Redis 멱등성 키를 삭제하여 Kafka 재시도 시 다시 처리될 수 있도록 수정했다.
```

---

## 2. Consumer catch 블록에서 markFailed 후 예외 재전파

`NotificationEventConsumer`에서 예외 발생 시 `markFailed(eventId)`를 호출한 뒤 예외를 다시 던지도록 수정했다.

```text
Consumer 처리 실패 시 멱등성 키를 삭제하고, 예외를 다시 던져 Kafka ErrorHandler가 재시도/DLT를 수행하도록 했다.
```

---

## 3. saveAndFlush()로 DB 저장 실패 즉시 감지

기존에는 `save()`를 사용했다.

```java
notificationRepository.save(notification);
```

JPA는 실제 INSERT를 트랜잭션 커밋 시점까지 지연할 수 있기 때문에, DB 저장 실패를 Consumer 내부 catch에서 감지하지 못할 수 있다.

그래서 `saveAndFlush()`로 변경했다.

```text
DB 저장 실패를 Consumer 내부에서 즉시 감지하여 catch 블록과 Kafka 재시도 흐름으로 연결되도록 했다.
```

---

## 변경 후 처리 흐름

```text
Consumer 처리 시작
 → eventId Redis 저장
 → Notification DB 저장 실패
 → catch 진입
 → markFailed(eventId)
 → Redis 멱등성 키 삭제
 → 예외 재전파
 → Kafka ErrorHandler 동작
 → 1초 간격 3회 재시도
 → 성공 시 정상 처리 완료
```

계속 실패하면 다음 흐름으로 이어진다.

```text
3회 재시도 실패
 → notifications.DLT 발행
 → DLT Consumer에서 실패 메시지 로그 기록
```

```text
Consumer 예외 발생 시 1초 간격으로 3회 재시도하고, 최종 실패 시 DLT로 보내도록 설정했다.
```

```text
최종 실패한 메시지를 DLT에서 수신해 운영 로그로 확인할 수 있도록 했다.
```

---

## 결과

기존에는 알림 처리 실패가 발생해도 Kafka가 정상 처리로 판단하여 재시도와 DLT가 동작하지 않을 수 있었다.

개선 후에는 실패 시 Redis 멱등성 키를 삭제하고 예외를 Kafka까지 전달하도록 변경했다.

그 결과 일시적인 DB 오류나 알림 저장 실패가 발생해도 Kafka 재시도가 정상 수행되고, 최종 실패한 메시지는 DLT로 이동해 추적할 수 있게 되었다.

---

## 영향 범위

해당 Consumer는 다음 알림들을 처리한다.

- 매칭 알림
- 채팅 알림
- 노쇼 알림
- 신고 알림
- 문의 알림
- 결제 알림

따라서 이번 수정은 특정 알림 하나가 아니라 전체 알림 시스템의 신뢰성을 높이는 변경이었다.

---

## 배운 점

처음에는 Consumer에서 예외를 다시 던지면 Kafka 재시도 문제가 해결될 것이라고 생각했다.

하지만 실제로는 Redis 멱등성 키가 처리 시작 시점에 먼저 저장되기 때문에, 실패 후 재시도 메시지가 중복으로 판단되어 skip될 수 있었다.

이번 경험을 통해 Kafka 재시도, DLT, 멱등성은 따로 보는 기능이 아니라 함께 설계해야 한다는 점을 배웠다.
