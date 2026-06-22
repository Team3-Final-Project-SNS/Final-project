# 정호진 트러블슈팅

만남 인증, 위치, 관리자, 캐싱 도메인에서 발생한 주요 문제와 해결 과정을 정리한 문서입니다.

<a id="노쇼-상태-2단계-분리-트러블슈팅"></a>

<details>
<summary><strong>노쇼 상태 2단계 분리 트러블슈팅</strong></summary>

## 노쇼 상태 2단계 분리 트러블슈팅

## 배경

한끼팟은 약속 장소에 나타나지 않은 사용자를 자동으로 판정하는 노쇼 시스템을 갖추고 있다. 노쇼 판정은 두 단계로 이루어진다.

- **GPS 단계**: 약속 시간 + 30분이 지났는데 장소 인증을 하지 않은 경우
- **QR 단계**: 양측 GPS 인증은 완료됐지만 QR 스캔 시간(30분)이 만료된 경우

이 두 배치 메서드(`judgeGpsNoShow`, `judgeQrNoShow`)는 10분마다 자동 실행된다.

---

## 문제 상황

노쇼 배치 로직을 구현하면서 `judgeGpsNoShow()` / `judgeQrNoShow()`에서 `matchService.markXxxNoShow()`를 호출하고 있었다.

```java
// judgeGpsNoShow() 내부 — 문제가 된 코드
if (!authorVerified && !applicantVerified) {
    meetVerification.markBothNoShow();
    matchService.markBothNoShow(meetVerification.getMatchId()); // ← 문제
    userLocationService.deleteLocationsByMatchId(...);
    chatService.deactivateChatRoom(currentPostId);
    notificationPublisher.sendNoShowWarning(...);
}
```

`matchService.markBothNoShow()` 내부를 보면:

```java
// MatchServiceImpl.markBothNoShow()
public void markBothNoShow(Long matchId) {
    Match match = getMatchById(matchId);
    match.markNoShow(MatchStatus.BOTH_NO_SHOW);
    postService.completePost(match.getPostId());

    Post post = postService.getPostById(match.getPostId());
    userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);    // 즉시 몰수
    userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId); // 즉시 몰수
}
```

즉 노쇼 판정 배치가 실행되는 순간 **포인트 몰수가 즉시** 발생하고 있었다.

---

## 정책과의 불일치

SA 문서에는 다음과 같이 명시되어 있다.

> "노쇼 예정으로 먼저 분류. 24시간 이내 이의제기가 없을 경우 노쇼로 판정한다."
> 

즉 노쇼 판정 시점의 흐름은 다음과 같아야 한다.

```
GPS/QR 만료 감지
    ↓
_NO_SHOW 상태로 전환 (예정 상태)
    ↓
24시간 이의제기 가능 구간
    ↓
이의제기 없거나 기각 시 → 노쇼 확정 → 포인트 몰수
이의제기 채택 시 → 노쇼 취소 → 포인트 환급
```

그런데 기존 코드는 **예정 판정 즉시 포인트를 몰수**하고 있었다. 이렇게 되면 이의제기가 채택되었을 때 이미 몰수된 포인트를 역으로 돌려줘야 하는 복잡한 복원 로직이 필요해지고, 사용자 입장에서도 이의제기 결과가 나오기 전에 포인트가 빠지는 불합리한 상황이 발생한다.

---

## 원인 분석

`markXxxNoShow()`는 원래 **Match 상태 전환 + Post 완료 처리 + 포인트 정산**을 한 번에 처리하도록 설계된 메서드였다. 이 메서드는 Admin 도메인의 이의제기 판정 등 여러 곳에서 호출되고 있었기 때문에 메서드 자체를 수정하면 다른 도메인에 영향을 줄 수 있었다.

핵심 문제는 이 메서드를 **노쇼 예정 판정 시점**에 호출하고 있었다는 것이다. 메서드가 잘못된 게 아니라 **호출 시점이 잘못**된 것이었다.

---

## 해결 방법

### 접근 방식 결정

세 가지 방법을 고려했다.

**방법 A**: `markXxxNoShow()`에서 포인트 처리 코드를 제거하고 별도 메서드로 분리

→ 이미 이 메서드를 쓰는 다른 도메인 코드가 많아서 영향 범위가 너무 넓음. 기각.

**방법 B**: `judgeNoShowConfirmed()`에서 `UserPointService`를 직접 호출

→ `authorDeposit`, `applicantDeposit` 값을 꺼내려면 `MatchInfoDto`, `PostInfoDto`에 예치금 필드를 추가해야 했고, 이 DTO를 쓰는 다른 도메인 코드도 전부 바뀌어야 함. 기각.

**방법 C**: `markXxxNoShow()` 호출 위치를 노쇼 예정 배치에서 노쇼 확정 배치로 이동

→ 기존 메서드도, DTO도 건드리지 않고 **호출 시점만 변경**. 채택.

---

### 구현

### 1단계 — 노쇼 예정 배치에서 `matchService.markXxxNoShow()` 제거

```java
// judgeGpsNoShow() — 수정 후
if (!authorVerified && !applicantVerified) {
    meetVerification.markBothNoShow(); // MeetVerification 상태만 변경
    // matchService.markBothNoShow() 제거 — 포인트 처리는 24시간 후 확정 배치에서
    userLocationService.deleteLocationsByMatchId(meetVerification.getMatchId());
    chatService.deactivateChatRoom(currentPostId);
    notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), meetVerification.getMatchId());
    notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), meetVerification.getMatchId());
}
```

이 시점에는 `MeetVerification`의 상태만 `_NO_SHOW`로 바꾸고, `Match`/`Post` 상태와 포인트는 건드리지 않는다.

### 2단계 — `judgeNoShowConfirmed()` 신규 추가

### 코드

```java
@Override
@Transactional
public void judgeNoShowConfirmed() {

    // noShowDecidedAt이 24시간 이전인 _NO_SHOW 상태 건 전체 조회
    LocalDateTime deadline = LocalDateTime.now().minusHours(NO_SHOW_CONFIRM_HOURS);

    List<MeetVerification> noShowList = meetVerificationRepository
            .findAllByStatusInAndNoShowDecidedAtBefore(NO_SHOW_STATUSES, deadline);

    if (noShowList.isEmpty()) return;

    List<Long> matchIds = noShowList.stream().map(MeetVerification::getMatchId).toList();

    Map<Long, MatchInfoDto> matchInfoMap = matchService.getMatchInfos(matchIds);
    Map<Long, PostInfoDto> postInfoMap = postQueryService.getPostInfos(...);

    // 관리자가 아직 검토 중인 이의제기(SUBMITTED/UNDER_REVIEW/HOLD)가 있는 건은 스킵
    Set<Long> activeDisputeMatchIds = disputeService.getMatchIdsWithActiveDispute(matchIds);

    for (MeetVerification meetVerification : noShowList) {
        Long matchId = meetVerification.getMatchId();

        if (activeDisputeMatchIds.contains(matchId)) continue; // 이의제기 검토 중 → 스킵

        VerificationStatus status = meetVerification.getStatus();

        // matchService.markXxxNoShow() 호출
        // → Match 상태 전환 + Post 완료 + 포인트 정산까지 한 번에 처리
        if (status == VerificationStatus.BOTH_NO_SHOW) {
            matchService.markBothNoShow(matchId);
            notificationPublisher.sendNoShowConfirmed(postInfoDto.authorId(), matchId);
            notificationPublisher.sendNoShowConfirmed(matchInfoDto.applicantId(), matchId);
        } else if (status == VerificationStatus.GUEST_NO_SHOW) {
            matchService.markApplicantNoShow(matchId);
            notificationPublisher.sendNoShowConfirmed(matchInfoDto.applicantId(), matchId);
        } else if (status == VerificationStatus.HOST_NO_SHOW) {
            matchService.markAuthorNoShow(matchId);
            notificationPublisher.sendNoShowConfirmed(postInfoDto.authorId(), matchId);
        }

        // NO_SHOW_CONFIRMED 상태로 전환 → 다음 배치에서 중복 실행 방지
        meetVerification.confirmNoShow();
    }
}
```

### 3단계 — `NO_SHOW_CONFIRMED` 상태 추가

`judgeNoShowConfirmed()`는 10분마다 반복 실행되는 배치다. 한 번 처리된 건이 다음 실행에서 또 처리되는 걸 막아야 했다. 처음에는 별도 플래그 컬럼 추가를 고려했지만, `VerificationStatus`에 `NO_SHOW_CONFIRMED`를 추가하는 방식이 더 명확했다. 이 상태는 `NO_SHOW_STATUSES` 목록에 포함되지 않으므로 다음 배치에서 조회 자체가 되지 않는다.

```java
// VerificationStatus.java
NO_SHOW_CONFIRMED("노쇼 확정"), // 24시간 경과 후 최종 확정

// MeetVerification.java
public void confirmNoShow() {
    this.status = VerificationStatus.NO_SHOW_CONFIRMED;
}
```

### 4단계 — 이의제기 검토 중인 건 스킵 처리

24시간이 지났더라도 관리자가 이의제기를 검토 중인 건은 포인트 몰수를 하면 안 된다. 이를 위해 `DisputeService`에 `getMatchIdsWithActiveDispute()` 메서드를 추가했다.

```java
// SUBMITTED: 제출 완료, 검토 대기
// UNDER_REVIEW: 관리자 검토 중
// HOLD: 보류 — 재이의제기 대기 중
// REJECTED는 포함 안 함 → 기각 확정된 건은 노쇼 확정 처리 대상
List<DisputeStatus> activeStatuses = List.of(
        DisputeStatus.SUBMITTED,
        DisputeStatus.UNDER_REVIEW,
        DisputeStatus.HOLD
);
```

`REJECTED`를 포함하지 않은 이유가 중요하다. 기각된 이의제기는 관리자가 이미 "노쇼가 맞다"고 판정한 것이므로 배치에서 정상적으로 포인트 몰수가 이루어져야 한다. 만약 `REJECTED`까지 스킵하면 기각된 건은 영원히 포인트 몰수가 안 되는 버그가 생긴다.

---

## 스케줄러 실행 방식 변경 — `fixedDelay` → `cron`

### `fixedDelay`란

스프링의 `@Scheduled(fixedDelay = 600000)`은 **이전 실행이 완료된 시점으로부터** 지정한 시간(ms)이 지난 후 다음 실행을 트리거하는 방식이다.

```
서버 시작: 13:03:27
1회 실행: 13:13:27 (시작 후 10분)
2회 실행: 13:23:27
3회 실행: 13:33:27
```

문제는 **서버 시작 시각에 종속**된다는 점이다. 서버가 재시작되거나 배포 타이밍이 달라질 때마다 실행 시각이 바뀐다.

```
배포 A: 서버 13:03 시작 → 13:13, 13:23, 13:33...
배포 B: 서버 13:07 시작 → 13:17, 13:27, 13:37...
```

노쇼 판정 배치는 "24시간 경과 여부"를 기준으로 포인트 몰수까지 하는 민감한 로직이기 때문에, 실행 타이밍이 배포마다 달라지면 예측하기 어렵고 테스트/검증도 힘들어진다.

---

### `cron`이란

`cron`은 **유닉스 계열 시스템에서 오래전부터 사용해온 작업 스케줄링 표현식**이다. 스프링의 `@Scheduled(cron = "...")`은 이 표현식을 그대로 지원한다.

cron 표현식은 6개 필드로 구성된다:

```
초  분  시  일  월  요일
*   *   *   *   *   *
```

이번에 적용한 표현식:

```
"0 0/10 * * * *"
 ↑  ↑   ↑  ↑  ↑  ↑
 초  분  시  일  월  요일

0      → 0초에
0/10   → 0분부터 10분 간격으로 (0, 10, 20, 30, 40, 50분)
*      → 매 시각
*      → 매일
*      → 매월
*      → 매 요일
```

결과적으로 **매 시각 0, 10, 20, 30, 40, 50분 정각 0초**에 실행된다.

```
13:00:00 실행
13:10:00 실행
13:20:00 실행
...
```

서버가 13:03에 시작되든 13:07에 시작되든 상관없이, 다음 정각 10분 단위 시각이 되면 실행된다.

---

### `fixedDelay` vs `cron` 비교

|  | `fixedDelay` | `cron` |
| --- | --- | --- |
| 기준 | 이전 실행 완료 시각 | 절대 시각 (시스템 클럭) |
| 서버 재시작 영향 | 시작 시각에 따라 실행 시각 달라짐 | 영향 없음 |
| 실행 시각 예측 | 어려움 | 명확함 |
| 표현식 복잡도 | 단순 (ms 숫자) | 6개 필드 표현식 |
| 적합한 상황 | 실행 간격만 중요할 때 | 특정 시각에 맞춰 실행해야 할 때 |

---

### 변경 이유 요약

노쇼 판정 배치는 "noShowDecidedAt + 24시간"이라는 시간 기준으로 포인트 몰수까지 하는 민감한 로직이다. `fixedDelay`를 쓰면 배포 타이밍마다 실행 시각이 달라져서 예측과 검증이 힘들어진다. `cron`으로 변경해서 서버 시작 시각과 무관하게 매 10분 정각에 고정 실행되도록 했다.

---

## 최종 흐름

```
judgeGpsNoShow / judgeQrNoShow (10분마다)
    → meetVerification.markXxxNoShow()  ← MeetVerification 상태만 _NO_SHOW로 변경
    → 위치 데이터 삭제
    → 채팅방 비활성화
    → sendNoShowWarning() 발송
    ← Match/Post 상태, 포인트 그대로

        24시간 이의제기 가능 구간

judgeNoShowConfirmed (10분마다)
    → 24시간 경과 건 조회
    → 이의제기 SUBMITTED/UNDER_REVIEW/HOLD → 스킵
    → 이의제기 없거나 REJECTED → 확정 처리
        → matchService.markXxxNoShow()
            → Match 상태 전환
            → Post 완료 처리
            → 포인트 몰수 / 피해자 환급
        → sendNoShowConfirmed() 발송
        → meetVerification.confirmNoShow() → NO_SHOW_CONFIRMED
```

---

## 변경된 파일 목록

| 파일 | 변경 내용 |
| --- | --- |
| `VerificationStatus.java` | `NO_SHOW_CONFIRMED` 추가 |
| `DisputeStatus.java` | `HOLD` 추가 |
| `MeetVerification.java` | `confirmNoShow()` 메서드 추가 |
| `MeetVerificationRepository.java` | `findAllByStatusInAndNoShowDecidedAtBefore()` 추가 |
| `MeetVerificationService.java` | `judgeNoShowConfirmed()` 선언 추가 |
| `MeetVerificationServiceImpl.java` | GPS/QR 배치에서 `matchService.markXxxNoShow()` 제거, `judgeNoShowConfirmed()` 신규 추가 |
| `NoShowScheduler.java` | `judgeNoShowConfirmed()` 추가, 배치 주기 1분 → 10분 변경 |
| `DisputeService.java` | `getMatchIdsWithActiveDispute()` 추가 |
| `DisputeServiceImpl.java` | `getMatchIdsWithActiveDispute()` 구현 추가 |
| `DisputeRepository.java` | `findMatchIdsByMatchIdInAndStatusIn()` 추가 |

---

## 배운 점

**메서드를 수정하지 않고 호출 시점을 바꾸는 것만으로도 문제를 해결할 수 있다.** 처음에는 `markXxxNoShow()`에서 포인트 처리 코드를 제거하거나 DTO에 필드를 추가하는 방향을 고려했는데, 두 방법 모두 영향 범위가 너무 넓었다. 기존 코드를 최대한 건드리지 않고 호출 위치만 옮기는 방식이 가장 안전했다.

**배치 로직에서 중복 실행 방지는 필수다.** 처음에는 단순히 시간 조건만 체크했는데, 배치가 10분마다 도는 이상 한 번 처리된 건이 계속 조회되어 알림이 반복 발송되는 문제가 있었다. 상태값으로 처리 완료 여부를 표시해서 조회 조건 자체에서 제외시키는 방식이 가장 명확하고 깔끔했다.

**"이의제기 스킵" 조건에서 `REJECTED` 제외 여부는 꼼꼼히 따져야 했다.** 처음에는 이의제기가 있으면 무조건 스킵하는 방향으로 구현했다가, REJECTED 케이스에서 포인트 몰수가 영원히 안 되는 버그를 발견했다. 상태별로 의미를 하나씩 따져보면서 "검토 중인 건"과 "이미 결론 난 건"을 구분해야 했다.

---

</details>

---

<a id="gps-장소-인증-위치-표시-트러블슈팅"></a>

<details>
<summary><strong>GPS 장소 인증 위치 표시 트러블슈팅</strong></summary>

## GPS 장소 인증 위치 표시 트러블슈팅

## 1. 문제 상황

장소 인증 화면에서 등록자와 신청자가 같은 `matchId`로 접속했고, 둘 다 약속 장소 반경 안에 들어왔는데도 상대방 위치가 지도에 표시되지 않았다.

확인한 URL 예시:

```text
<http://localhost:5173/matches/10/place-verification>
```

프론트 네트워크 응답에서는 내 위치는 내려오지만 상대 위치가 `null`로 내려왔다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "myLocation": {
      "latitude": 37.60,
      "longitude": 126.72,
      "role": "AUTHOR"
    },
    "opponentLocation": null
  }
}
```

---

## 2. 원인 요약

핵심 원인은 **DB에 저장되는 위도/경도 소수점 정밀도 문제**였다.

원래 `user_locations` 테이블의 `latitude`, `longitude` 컬럼이 충분한 소수점 자릿수를 보존하지 못해서 실제 GPS 좌표가 다음처럼 잘려 저장되고 있었다.

```
실제 좌표: 37.5985370, 126.7155210
저장/조회 좌표: 37.60, 126.72
```

GPS 좌표에서 소수점 몇 자리가 잘리면 실제 위치가 수십 미터에서 수백 미터 이상 달라질 수 있다.

그 결과 백엔드는 상대방이 반경 안에 있는지 거리 계산을 할 때, 잘린 좌표 기준으로 계산하게 되었고 상대방이 반경 밖이라고 판단해서 `opponentLocation: null`을 내려보냈다.

---

## 3. 관련 개념: DECIMAL(10,7)

위도/경도 컬럼에 사용한 설정은 다음과 같다.

```java
@Column(name = "latitude", nullable = false, precision = 10, scale = 7)
private BigDecimal latitude;

@Column(name = "longitude", nullable = false, precision = 10, scale = 7)
private BigDecimal longitude;
```

DB 기준으로 보면 다음 SQL과 같은 의미다.

```sql
ALTER TABLE user_locations
MODIFY latitude DECIMAL(10,7),
MODIFY longitude DECIMAL(10,7);
```

### `DECIMAL(10,7)`의 의미

```
precision = 10
전체 숫자 자릿수 최대 10자리

scale = 7
소수점 아래 7자리
```

즉 `DECIMAL(10,7)`은 아래와 같은 값을 저장할 수 있다.

```
37.5985370
126.7155210
```

여기서 중요한 점은 `DECIMAL(10,7)`이 특정 값을 하드코딩하는 것이 아니라, **좌표를 저장할 수 있는 숫자 형식을 정하는 것**이라는 점이다.

예를 들어:

```
37.5985370
37.6000000
35.1795543
126.7155210
127.0276100
```

이런 다양한 좌표값을 소수점 7자리까지 보존해서 저장할 수 있다.

---

## 4. 왜 GPS 좌표에는 소수점 정밀도가 중요한가?

위도/경도는 소수점 자릿수에 따라 위치 정확도가 크게 달라진다.

대략적인 기준은 다음과 같다.

| 좌표 자릿수 | 대략적인 위치 정확도 |
| --- | --- |
| 소수점 2자리 | 약 1km 단위 |
| 소수점 3자리 | 약 100m 단위 |
| 소수점 4자리 | 약 10m 단위 |
| 소수점 5자리 | 약 1m 단위 |
| 소수점 6자리 이상 | 1m 이하 수준 |

이번 기능은 장소 반경 `50m` 또는 `60m` 안에 들어왔는지 판단해야 한다.

따라서 좌표가 `37.60`, `126.72`처럼 소수점 2자리까지만 저장되면 거리 계산이 거의 의미가 없어질 수 있다.

장소 인증, 실시간 위치 공유, 근처 도착 여부 판단 같은 기능에서는 위도/경도 컬럼의 정밀도를 반드시 충분히 확보해야 한다.

---

## 5. DB 초기화가 필요했던 이유

엔티티 코드에 아래처럼 `precision`, `scale`을 추가해도 이미 만들어진 DB 테이블 구조가 자동으로 바뀌지 않을 수 있다.

```java
@Column(name = "latitude", nullable = false, precision = 10, scale = 7)
private BigDecimal latitude;
```

특히 Docker MySQL을 사용하고 있고 기존 볼륨에 테이블이 이미 생성되어 있었다면, 애플리케이션을 다시 실행해도 기존 컬럼 타입이 그대로 남아 있을 수 있다.

그래서 Docker Compose로 띄운 MySQL을 초기화하려면 볼륨까지 삭제해야 한다.

```bash
docker compose down -v
docker compose up -d
```

- `v` 옵션은 Docker Compose가 만든 볼륨을 함께 삭제한다.

즉 기존 MySQL 데이터와 테이블 구조가 초기화되고, 애플리케이션이 다시 실행될 때 현재 엔티티 기준으로 테이블이 새로 생성된다.

---

## 6. 수정 후 확인 결과

DB 초기화 후 다시 테스트했을 때 응답이 정상적으로 바뀌었다.

등록자 화면:

```json
{
  "myLocation": {
    "latitude": 37.5985370,
    "longitude": 126.7155210,
    "role": "AUTHOR"
  },
  "opponentLocation": {
    "latitude": 37.5985370,
    "longitude": 126.7155210,
    "role": "APPLICANT"
  }
}
```

신청자 화면:

```json
{
  "myLocation": {
    "latitude": 37.5985370,
    "longitude": 126.7155210,
    "role": "APPLICANT"
  },
  "opponentLocation": {
    "latitude": 37.5985370,
    "longitude": 126.7155210,
    "role": "AUTHOR"
  }
}
```

이제 양쪽 모두 `opponentLocation`이 `null`이 아니라 정상 좌표로 내려왔다.

즉 백엔드의 상대 위치 조회 로직 자체가 완전히 실패한 것이 아니라, DB 좌표 정밀도 문제 때문에 반경 체크에서 걸러지고 있었던 것이다.

---

## 7. 프론트 위치 마커 색상 문제

상대 위치가 보이기 시작한 뒤에도 지도 마커 색상이 이상하게 보이는 문제가 있었다.

처음 프론트 코드는 내 위치는 항상 파란색으로 표시하고, 상대방 위치는 `role` 기준으로 색을 정하고 있었다.

기존 구조는 대략 다음과 같았다.

```tsx
// 내 위치
background: #2196f3;

// 상대 위치
const markerColor = opponentPosition.role === 'AUTHOR'
  ? '#2196f3'
  : '#F97316';
```

이 경우 문제가 생긴다.

| 접속자 | 내 역할 | 내 마커 | 상대 역할 | 상대 마커 |
| --- | --- | --- | --- | --- |
| 등록자 | AUTHOR | 파란색 | APPLICANT | 주황색 |
| 신청자 | APPLICANT | 파란색 | AUTHOR | 파란색 |

신청자 화면에서는 내 위치도 파란색이고, 상대방인 등록자도 파란색이 된다.

그래서 사용자가 보기에는 상대방 위치가 없거나, 같은 색이라 구분이 안 되는 것처럼 보일 수 있다.

---

## 8. 프론트 색상 정책 결정

장소 인증 화면의 목적은 “등록자/신청자 역할 구분”보다 “내 위치와 상대 위치 구분”에 가깝다.

따라서 색상 정책은 아래처럼 가는 것이 더 적절하다.

```
나 = 항상 파란색
상대방 = 항상 주황색
```

역할별 색상 고정 방식:

```
등록자 = 주황색
신청자 = 파란색
```

이 방식은 관리자 화면이나 운영 화면처럼 역할 구분이 중요한 경우에는 적합할 수 있다.

하지만 장소 인증 화면에서는 사용자가 자신의 현재 위치와 상대방의 현재 위치를 빠르게 구분하는 것이 더 중요하다.

그래서 최종적으로 프론트는 역할과 상관없이 상대방 마커를 항상 주황색으로 표시하도록 수정했다.

```tsx
const markerColor = '#F97316';
```

---

## 9. 현재 최종 동작 방식

장소 인증 화면의 현재 위치 표시 정책은 다음과 같다.

```
내 위치: 파란색
상대방 위치: 주황색
```

역할과 관계없이 동일하다.

| 내 역할 | 내 마커 | 상대 마커 |
| --- | --- | --- |
| 등록자 | 파란색 | 주황색 |
| 신청자 | 파란색 | 주황색 |

이 방식이 사용자 입장에서 가장 직관적이다.

---

## 10. 추가로 체크할 부분

### 10.1 반경 기준 통일

현재 코드상 일부 로직은 `60m`, 문서나 요구사항은 `50m` 기준일 수 있다.

확인해야 할 부분:

```
백엔드 위치 공개 반경
백엔드 장소 인증 반경
프론트 거리 표시
프론트 진행바 기준
카카오 지도 원 반경
```

정책이 `50m`라면 전체를 `50m`로 통일해야 한다.

정책이 `60m`라면 문서와 UI 문구도 `60m`로 맞춰야 한다.

중요한 것은 프론트와 백엔드 기준이 서로 다르면 안 된다는 점이다.

---

### 10.2 상대방 위치 공개 정책

신상 노출 위험을 줄이기 위해 상대방 위치는 항상 보여주면 안 된다.

권장 정책:

```
상대방이 약속 장소 반경 안에 있을 때만 opponentLocation 반환
상대방이 반경 밖이면 opponentLocation: null 반환
```

즉 백엔드에서 위치 공개 여부를 제어하는 것이 맞다.

프론트에서만 숨기면 네트워크 응답에는 여전히 상대 좌표가 내려오기 때문에 보안상 의미가 약하다.

---

### 10.3 내 위치와 상대 위치가 완전히 겹치는 경우

테스트 환경에서는 두 브라우저가 같은 PC 위치를 사용하기 때문에 내 위치와 상대 위치가 완전히 겹칠 수 있다.

이 경우 주황색 마커가 파란색 뒤에 가려져 보일 가능성이 있다.

추가 개선 방향:

```
상대방 마커 z-index 높이기
상대방 마커 크기 조금 다르게 하기
마커 테두리나 그림자 다르게 주기
두 위치가 매우 가까우면 살짝 offset 적용
```

다만 실제 모바일 환경에서는 두 사용자의 GPS 좌표가 완전히 같을 가능성은 낮다.

---

### 10.4 위치 업데이트와 조회 순서

현재 프론트는 주기적으로 위치를 서버에 전송하고, 동시에 위치 조회를 한다.

이때 위치 전송 요청이 완료되기 전에 조회 요청이 먼저 끝나면 아주 잠깐 이전 값이나 `null`이 보일 수 있다.

개선 방향:

```tsx
await updateMyLocation(...)
const locRes = await getLocations(...)
```

즉 내 위치 전송이 끝난 뒤 위치 조회를 하면 더 안정적이다.

이번 문제의 핵심 원인은 아니었지만, 실시간 위치 기능 안정성을 높이려면 검토할 수 있다.

---

## 11. 최종 정리

이번 문제는 크게 두 가지였다.

### 첫 번째 문제: 상대방 위치가 null

원인:

```
user_locations.latitude / longitude 컬럼의 소수점 정밀도 부족
```

해결 방향:

```
DECIMAL(10,7)로 위도/경도 저장 정밀도 확보
Docker MySQL 볼륨 초기화 후 테이블 재생성
```

결과:

```
opponentLocation 정상 반환
```

---

### 두 번째 문제: 마커 색상 혼동

원인:

```
내 위치는 파란색 고정
상대방 위치는 role 기준 색상
```

이 때문에 신청자 화면에서는 내 위치와 상대방 위치가 둘 다 파란색이 될 수 있었다.

해결 방향:

```
내 위치 = 항상 파란색
상대방 위치 = 항상 주황색
```

결과:

```
등록자/신청자 여부와 상관없이 사용자가 직관적으로 위치를 구분할 수 있음
```

---

## 12. 배운 점

- GPS 좌표는 DB 컬럼 정밀도가 매우 중요하다.
- 장소 인증처럼 반경이 작은 기능에서는 위도/경도 소수점이 잘리면 기능이 정상 동작하지 않는다.
- `DECIMAL(10,7)`은 값을 하드코딩하는 것이 아니라 좌표를 저장할 수 있는 숫자 형식을 정의하는 것이다.
- Docker MySQL은 기존 볼륨이 남아 있으면 엔티티 수정만으로 테이블 구조가 바뀌지 않을 수 있다.
- 보안상 상대방 위치 공개 여부는 프론트가 아니라 백엔드에서 제어해야 한다.
- 사용자 화면에서는 역할 기준 색상보다 “나 / 상대방” 기준 색상이 더 직관적이다.

---

</details>

---

<a id="트러블-슈팅-redis-캐시-역직렬화-타입-불일치"></a>

<details>
<summary><strong>트러블 슈팅: Redis 캐시 역직렬화 타입 불일치</strong></summary>

## **트러블 슈팅: Redis 캐시 역직렬화 타입 불일치**

## 1. 문제 상황

게시글 목록 조회 API에 Redis 캐싱을 적용한 뒤, 캐시가 생성된 이후 요청에서 500 에러가 발생했다.

단건 API 호출 결과:

```
GET /api/v1/posts?status=OPEN&page=0&size=20
→ HTTP 500 Internal Server Error
```

서버 로그에는 다음 예외가 발생했다.

```
class java.lang.Integer cannot be cast to class java.lang.Long
```

로그 위치:

```
PostServiceImpl.getPosts()
PostController.getPosts()
```

## 2. 발생 지점

캐싱 대상은 다음 메서드였다.

```java
userService.getUserIdsByUniversityId(universityId)
```

이 메서드는 같은 대학교 유저 ID 목록을 조회한다.

```java
public List<Long> getUserIdsByUniversityId(Long universityId)
```

게시글 목록 조회 로직에서는 이 값을 기반으로 게시글 조회 쿼리를 실행한다.

```java
postRepository.findByAuthorIdInAndStatus(
        visibleAuthorIds,
        status,
        pageable
);
```

따라서 캐시에서 복원된 유저 ID 목록은 `List<Long>` 타입으로 유지되어야 한다.

---

## 3. 원인

초기 `CacheConfig`에서는 공통 Redis 캐시 직렬화 방식으로 `GenericJackson2JsonRedisSerializer`를 사용했다.

```java
GenericJackson2JsonRedisSerializer jsonSerializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);
```

이 설정은 DTO, Map, 일반 객체를 JSON 형태로 저장하기에는 적합하다.

하지만 `List<Long>`처럼 제네릭 숫자 컬렉션을 캐싱할 때 문제가 발생했다.

Redis에 저장된 JSON 숫자 배열을 다시 읽는 과정에서 Jackson이 숫자 값을 `Long`이 아니라 `Integer`로 복원했다.

결과적으로 캐시에서 복원된 값은 기대한 타입과 달랐다.

```
기대:
List<Long>

실제:
List<Integer>
```

이후 서비스 로직에서 해당 값을 `Long`으로 다루는 과정에서 다음 예외가 발생했다.

```
Integer cannot be cast to Long
```

---

## 4. 해결 방향

처음에는 `sameUniversityUserIds` 캐시에만 직접 `JdkSerializationRedisSerializer`를 적용하는 방식을 고려했다.

하지만 `CacheConfig`는 팀 전체가 사용하는 공용 캐시 설정 파일이다.

따라서 특정 캐시에만 임시로 설정을 박아두기보다, **타입 보존이 필요한 캐시에 재사용할 수 있는 공용 설정 메서드**를 제공하는 방식으로 변경했다.

최종 구조는 다음과 같다.

```
기본 캐시
→ JSON 직렬화 사용

타입 보존이 중요한 캐시
→ JDK 직렬화 공용 설정 사용
```

---

## 5. 적용 코드

### 5.1 기본 JSON 캐시 설정

대부분의 캐시는 JSON 형태로 저장해도 충분하므로, 기본 설정은 `GenericJackson2JsonRedisSerializer`를 사용한다.

```java
private RedisCacheConfiguration createDefaultJsonCacheConfig() {

    ObjectMapper objectMapper = new ObjectMapper();

    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer(objectMapper);

    return RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                            jsonSerializer
                    )
            );
}
```

이 설정의 역할은 다음과 같다.

| 설정 | 역할 |
| --- | --- |
| `GenericJackson2JsonRedisSerializer` | 캐시 값을 JSON 형태로 저장 |
| `JavaTimeModule` | `LocalDateTime`, `LocalDate`, `LocalTime` 직렬화/역직렬화 지원 |
| `WRITE_DATES_AS_TIMESTAMPS` 비활성화 | 날짜/시간 값을 ISO-8601 문자열 형태로 저장 |
| `disableCachingNullValues()` | null 결과 캐싱 방지 |

---

### 5.2 타입 보존용 JDK 직렬화 공용 설정

`List<Long>`처럼 타입 보존이 중요한 캐시는 JSON 역직렬화 과정에서 숫자 타입이 달라질 수 있다.

이를 해결하기 위해 `CacheConfig`에 공용 메서드를 추가했다.

```java
public static RedisCacheConfiguration jdkSerializedCacheConfig(
        RedisCacheConfiguration baseConfig, Duration ttl) {
    return baseConfig
            .entryTtl(ttl)
            .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                            new JdkSerializationRedisSerializer()
                    )
            );
}
```

이 메서드는 다음 상황에서 사용할 수 있다.

| 상황 | 이유 |
| --- | --- |
| `List<Long>` 캐싱 | JSON 역직렬화 시 `Integer`로 복원될 수 있음 |
| 제네릭 컬렉션 캐싱 | 런타임에 정확한 타입 보존이 필요함 |
| 타입 안정성이 중요한 캐시 | Redis 값을 사람이 읽는 것보다 타입 보존이 우선 |

---

### 5.3 sameUniversityUserIds 캐시에 적용

게시글 목록 조회에서 사용하는 같은 대학교 유저 ID 목록 캐시는 `List<Long>` 타입이다.

따라서 해당 캐시에 JDK 직렬화 공용 설정을 적용했다.

```java
return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaultConfig.entryTtl(DEFAULT_TTL))
        .withInitialCacheConfigurations(Map.of(
                PostCachePolicy.SAME_UNIVERSITY_USER_IDS,
                jdkSerializedCacheConfig(
                        defaultConfig,
                        PostCachePolicy.SAME_UNIVERSITY_USER_IDS_TTL
                )
        ))
        .build();
```

---

## 6. 왜 JDK 직렬화를 사용했는가

`sameUniversityUserIds` 캐시는 `List<Long>` 타입을 저장한다.

JSON 직렬화는 사람이 읽기 쉽고 일반 DTO 캐싱에 유리하지만, 제네릭 숫자 컬렉션을 복원할 때 `Integer`와 `Long` 타입이 기대와 다르게 복원될 수 있다.

반면 `JdkSerializationRedisSerializer`는 Java 객체 타입 정보를 함께 직렬화한다.

따라서 `List<Long>`을 저장하면 다시 읽을 때도 타입 정보가 보존된다.

이번 캐시는 Redis 값을 사람이 직접 읽기 쉽게 저장하는 것보다, 서비스 로직에서 `List<Long>` 타입을 안정적으로 유지하는 것이 더 중요했다.

---

## 7. 기존 캐시 삭제

직렬화 방식을 변경한 뒤에는 기존 Redis 캐시 값을 삭제해야 한다.

이전에 JSON 방식으로 저장된 캐시가 남아 있으면, 새로운 설정을 적용해도 기존 값을 읽는 과정에서 다시 타입 불일치 문제가 발생할 수 있다.

```bash
docker exec -it redis redis-cli DEL "post:sameUniversityUserIds::3"
```

삭제 후 게시글 목록 조회 API를 다시 호출하여 캐시를 새로 생성했다.

```
GET /api/v1/posts?status=OPEN&page=0&size=20
Authorization: Bearer {accessToken}
```

---

## 8. 검증 기준

수정 후 다음 항목을 확인한다.

| 검증 항목 | 기대 결과 |
| --- | --- |
| 첫 번째 API 요청 | Cache Miss, DB 조회 후 Redis 저장 |
| 두 번째 API 요청 | Cache Hit, Redis 값 사용 |
| API 응답 | HTTP 200 |
| 서버 로그 | `Integer cannot be cast to Long` 예외 미발생 |
| Redis key | `post:sameUniversityUserIds::{universityId}` 생성 |
| Redis TTL | 600초 이하 양수 |

---

## 9. 최종 정리

이번 문제는 Redis 연결 문제나 k6 문제가 아니라, 캐시 값의 역직렬화 타입 불일치 문제였다.

원인은 `List<Long>`을 JSON으로 캐싱한 뒤 다시 읽을 때 숫자 값이 `Integer`로 복원된 것이었다.

해결 방식은 다음과 같다.

```
1. 기본 캐시는 JSON 직렬화 유지
2. LocalDateTime 처리를 위해 JavaTimeModule 등록
3. 타입 보존이 중요한 캐시를 위한 JDK 직렬화 공용 설정 추가
4. sameUniversityUserIds 캐시는 JDK 직렬화 설정 사용
5. 기존 Redis 캐시 삭제 후 재생성
```

최종적으로 `CacheConfig`는 팀 공통 캐시 설정을 담당하고, 타입 보존이 필요한 캐시는 `jdkSerializedCacheConfig()`를 재사용할 수 있도록 구성했다.

---

</details>

---

<a id="만남-인증-그룹-매칭-대응-수정-설명"></a>

<details>
<summary><strong>만남 인증 그룹 매칭 대응 수정 설명</strong></summary>

## 만남 인증 그룹 매칭 대응 수정 설명

## 1. 기존에 발생하던 문제

기존 만남 인증 로직은 1:1 만남만 고려해서 구현되어 있었다.

1:1 만남에서는 하나의 Post에 하나의 Match만 존재하므로, 신청자가 QR을 스캔하는 순간 해당 Match와 Post를 동시에 완료 처리해도 문제가 없었다.

하지만 그룹 매칭에서는 하나의 Post에 여러 개의 Match가 존재한다.

예를 들면 다음과 같다.

```
Post 1개
 ├─ Match A: 신청자 A
 ├─ Match B: 신청자 B
 └─ Match C: 신청자 C
```

이 상황에서 기존 코드처럼 신청자 A가 QR을 스캔하자마자 Post 전체를 COMPLETED 처리하면, B와 C는 아직 QR 스캔을 하지 않았는데도 Post가 이미 완료 상태가 된다.

그 결과 다음과 같은 문제가 발생할 수 있었다.

```
1. 신청자 A가 QR 스캔
2. 기존 로직에서 Post 전체 COMPLETED 처리
3. 채팅방 비활성 예약
4. 등록자 책임비 환급
5. 이후 신청자 B 또는 C가 QR 스캔 시도
6. 이미 Post가 완료되어 정상 인증 흐름이 깨짐
```

즉, 기존 문제의 핵심은 다음과 같다.

```
QR은 등록자가 보여주는 공통 인증 수단인데,
신청자별 QR 스캔 완료 여부는 각각 따로 관리되어야 한다.
```

기존 코드는 이 두 개념을 분리하지 못하고 있었다.

---

## 2. 이번 수정의 핵심 방향

이번 수정의 핵심은 다음과 같다.

```
QR 토큰은 Post 단위로 1개만 발급한다.
QR 스캔 완료 여부는 Match / MeetVerification 단위로 각각 관리한다.
```

즉, 1:1이든 그룹이든 등록자 입장에서는 동일하게 QR 1개만 보여준다.

```
1:1 만남
등록자 화면: QR 1개

그룹 만남
등록자 화면: QR 1개
```

하지만 신청자 쪽에서는 각자 자신의 Match 기준으로 QR 스캔 여부를 따로 기록한다.

```
신청자 A
→ A의 Match
→ A의 MeetVerification
→ A가 스캔하면 A만 DONE / COMPLETED

신청자 B
→ B의 Match
→ B의 MeetVerification
→ B가 스캔하면 B만 DONE / COMPLETED
```

따라서 그룹 매칭에서는 일부 신청자가 먼저 QR 스캔을 완료해도 Post 전체는 바로 완료되지 않는다.

마지막 신청자까지 모두 스캔이 끝났을 때만 Post를 최종 완료 처리한다.

---

## 3. 수정 후 전체 흐름

### 3-1. 등록자 GPS 인증

등록자는 그룹 만남 전체의 호스트이므로, 등록자가 장소 인증을 한 번 완료하면 같은 Post에 속한 모든 MeetVerification에 등록자 장소 인증 시간이 전파된다.

```
등록자 GPS 인증
→ 같은 postId의 모든 Match 조회
→ 각 Match의 MeetVerification에 authorPlaceVerifiedAt 기록
```

이 처리를 하지 않으면 신청자 B, C의 MeetVerification에는 등록자 인증 시간이 null로 남을 수 있다.

그 상태로 노쇼 배치가 실행되면 실제로는 등록자가 도착했는데도 HOST_NO_SHOW로 오판될 수 있다.

---

### 3-2. 신청자 GPS 인증

신청자는 자기 Match에 대해서만 장소 인증을 수행한다.

```
신청자 A GPS 인증
→ A의 MeetVerification만 applicantPlaceVerifiedAt 기록

신청자 B GPS 인증
→ B의 MeetVerification만 applicantPlaceVerifiedAt 기록
```

신청자별 도착 여부는 독립적으로 관리되어야 하므로, 신청자 인증은 다른 신청자의 MeetVerification에 전파하지 않는다.

---

### 3-3. QR 토큰 발급

QR 토큰은 Post 기준 공통 토큰으로 발급한다.

즉, 그룹 매칭에서 신청자 A, B, C가 각각 다른 QR 토큰을 가지면 안 된다.

```
Post 1개
 ├─ Match A
 ├─ Match B
 └─ Match C

위 구조에서 QR 토큰은 Post 기준으로 1개만 사용한다.
```

기존 방식처럼 MeetVerification마다 UUID를 새로 만들면, 신청자별로 QR 토큰이 달라질 수 있다.

그러면 등록자가 화면에 띄운 QR과 신청자별로 저장된 QR 토큰이 서로 달라져서 QR 스캔 실패가 발생할 수 있다.

따라서 수정 후에는 다음 방식으로 처리한다.

```
1. 같은 postId에 이미 발급된 QR 토큰이 있는지 확인
2. 있으면 기존 토큰 재사용
3. 없으면 새 QR 토큰 생성
4. 현재 MeetVerification에 해당 공통 토큰 저장
```

MeetVerification에는 postId 필드를 추가하지 않는다.

postId는 이미 Match를 통해 알 수 있기 때문에, MeetVerification에 postId를 중복 저장하면 데이터 불일치 위험이 생긴다.

---

### 3-4. 등록자 QR 조회

등록자 QR 조회는 원칙적으로 postId 기준이 맞다.

이유는 등록자가 보여주는 QR은 특정 신청자 Match의 QR이 아니라, 해당 Post의 공통 QR이기 때문이다.

따라서 새 구조에서는 다음과 같이 처리한다.

```
등록자 QR 조회 요청
→ postId 기준 Post 조회
→ 요청자가 Post 작성자인지 검증
→ 같은 postId의 MeetVerification 목록 조회
→ VERIFIED 상태인 MeetVerification이 하나라도 있는지 확인
→ 공통 QR 토큰 반환
```

기존 matchId 기반 API는 호환을 위해 유지할 수 있지만, 내부에서는 matchId로 postId를 구한 뒤 postId 기준 QR 조회 메서드로 위임한다.

---

### 3-5. 신청자 QR 스캔

신청자 QR 스캔은 본인의 matchId 기준으로 처리한다.

단, QR 토큰 검증은 본인의 MeetVerification에 저장된 토큰이 아니라 Post 기준 공통 QR 토큰과 비교한다.

```
신청자 A QR 스캔
→ A가 해당 Match의 신청자인지 확인
→ A의 MeetVerification이 VERIFIED 상태인지 확인
→ Post 공통 QR 토큰과 요청 토큰 비교
→ A의 MeetVerification만 DONE 처리
→ A의 Match만 COMPLETED 처리
→ A 신청자 예치금 환급
```

여기서 중요한 점은 A가 스캔했다고 해서 Post 전체를 바로 완료하지 않는다는 것이다.

A 외에 아직 MATCHED 상태인 Match가 남아 있다면 Post는 계속 유지된다.

---

### 3-6. 마지막 신청자 스캔 시 Post 최종 완료

각 신청자가 QR을 스캔할 때마다 해당 Match만 COMPLETED 처리한다.

그리고 완료 처리 후 같은 postId에 아직 MATCHED 상태의 Match가 남아 있는지 확인한다.

```
남은 MATCHED Match가 있다
→ 아직 그룹 만남이 끝나지 않음
→ Post 완료 처리 안 함

남은 MATCHED Match가 없다
→ 모든 활성 Match가 종료됨
→ Post COMPLETED 처리
→ 등록자 책임비 환급
→ 채팅방 비활성 예약
```

따라서 Post 완료, 등록자 책임비 환급, 채팅방 비활성 예약은 마지막 신청자의 QR 스캔 시점에만 실행된다.

---

## 4. 구현한 메서드별 역할

## 4-1. MatchService.completeSingleMatch(Long matchId)

### 역할

QR 스캔이 성공한 신청자 1명의 Match만 COMPLETED 처리하는 메서드다.

### 책임

```
- Match 단건 상태를 MATCHED → COMPLETED로 변경
- 신청자 예치금 환급
- 같은 Post에 아직 MATCHED 상태의 Match가 남아 있는지 확인
- 이번 스캔이 마지막 스캔인지 boolean으로 반환
```

### 왜 필요한가?

기존 completeMatch()는 Match 단건 완료뿐 아니라 Post 완료, 등록자 환급, 채팅방 비활성 예약까지 한 번에 처리하는 구조였다.

1:1에서는 문제가 없지만, 그룹에서는 첫 번째 신청자만 스캔해도 Post 전체가 완료되는 문제가 생긴다.

그래서 QR 스캔 경로에서는 기존 completeMatch() 대신 completeSingleMatch()를 사용한다.

### 동작 예시

```
신청자 A QR 스캔
→ A Match COMPLETED
→ A 신청자 예치금 환급
→ B, C가 아직 MATCHED면 false 반환

신청자 C QR 스캔
→ C Match COMPLETED
→ C 신청자 예치금 환급
→ 남은 MATCHED가 없으면 true 반환
```

---

## 4-2. MatchService.completePostIfAllMatchesCompleted(Long postId)

### 역할

해당 Post의 모든 활성 Match가 종료되었을 때 Post 전체를 COMPLETED 처리하는 메서드다.

### 책임

```
- 같은 postId에 MATCHED 상태의 Match가 남아 있는지 재확인
- Post를 PESSIMISTIC_WRITE 락으로 조회
- 이미 COMPLETED면 아무것도 하지 않음
- Post 상태를 COMPLETED로 변경
- 등록자 책임비 환급
```

### 왜 필요한가?

마지막 신청자 QR 스캔 시점에만 Post 전체를 완료해야 한다.

또한 동시에 여러 신청자가 거의 같은 시점에 QR을 스캔할 수 있으므로, 등록자 책임비가 중복 환급되지 않도록 Post row에 락을 걸어야 한다.

PostServiceImpl에는 이미 getPostByIdWithLock() 메서드가 있으므로, 이를 활용한다.

### 멱등성

이미 Post가 COMPLETED 상태라면 바로 return한다.

이렇게 하면 같은 Post 완료 요청이 중복으로 들어와도 등록자 책임비 환급이 다시 실행되지 않는다.

---

## 4-3. MeetVerificationService.getMeetQrByPost(Long userId, Long postId)

### 역할

등록자가 Post 기준 공통 QR을 조회하는 메서드다.

### 책임

```
- postId로 Post 정보 조회
- 요청자가 해당 Post의 등록자인지 검증
- 같은 postId의 Match 목록 조회
- 같은 postId의 MeetVerification 목록 벌크 조회
- VERIFIED 상태인 MeetVerification이 하나라도 있는지 확인
- 공통 QR 토큰이 없으면 발급
- 공통 QR 토큰과 만료 시각 반환
```

### 왜 matchId가 아니라 postId 기준인가?

등록자가 보여주는 QR은 특정 신청자 한 명을 위한 QR이 아니다.

그룹 만남에서는 신청자가 여러 명이어도 등록자는 하나의 QR만 보여줘야 한다.

따라서 QR 조회 API는 postId 기준이 가장 자연스럽다.

기존 matchId 기반 getMeetQr()는 호환성을 위해 남겨둘 수 있지만, 내부에서는 postId 기준 메서드로 위임한다.

---

## 4-4. MeetVerificationService.getMeetQr(Long userId, Long matchId)

### 역할

기존 matchId 기반 QR 조회 API의 호환용 메서드다.

### 책임

```
- matchId로 MatchInfo 조회
- MatchInfo에서 postId 추출
- getMeetQrByPost(userId, postId)로 위임
```

### 왜 유지하는가?

이미 프론트나 기존 API에서 matchId 기반 QR 조회를 사용하고 있을 수 있기 때문이다.

다만 실제 QR 조회 로직은 postId 기준으로 통일한다.

---

## 4-5. MeetVerificationService.createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto)

### 역할

신청자가 QR을 스캔해서 만남 인증을 최종 완료하는 메서드다.

### 책임

```
- matchId로 신청자 본인의 MeetVerification 조회
- 요청자가 해당 Match의 신청자인지 검증
- 본인의 MeetVerification 상태가 VERIFIED인지 확인
- Post 기준 공통 QR 토큰 조회
- 요청 QR 토큰과 공통 QR 토큰 비교
- 본인의 MeetVerification만 DONE 처리
- 본인 Match 위치 데이터 삭제
- MatchService.completeSingleMatch(matchId) 호출
- 마지막 스캔이면 Post 완료와 등록자 환급 처리
- 마지막 스캔이면 채팅방 비활성 예약
```

### 기존 코드와 달라진 점

기존에는 신청자 한 명이 QR 스캔하면 바로 다음 작업을 했다.

```
- 채팅방 비활성 예약
- matchService.completeMatch(matchId)
```

이 흐름은 그룹 매칭에서 첫 스캔만으로 Post 전체가 종료될 수 있는 문제가 있었다.

수정 후에는 다음 흐름으로 변경했다.

```
- 신청자 본인의 MeetVerification만 DONE
- 신청자 본인의 Match만 COMPLETED
- 남은 MATCHED Match가 없을 때만 Post 최종 완료
```

---

## 4-6. issueQrTokenIfNeeded(MeetVerification meetVerification, Long postId)

### 역할

Post 기준 공통 QR 토큰을 발급하거나 기존 토큰을 재사용하는 private 헬퍼 메서드다.

### 책임

```
- 현재 MeetVerification에 이미 QR 토큰이 있으면 중복 발급하지 않음
- 양측 GPS 인증이 완료되지 않았으면 발급하지 않음
- 같은 postId에 이미 발급된 QR 토큰이 있는지 확인
- 기존 토큰이 있으면 재사용
- 기존 토큰이 없으면 새 UUID 기반 QR 토큰 생성
- 현재 MeetVerification에 QR 토큰과 만료 시각 저장
```

### 왜 postId를 파라미터로 받는가?

MeetVerification에 postId 필드를 추가하지 않기 위해서다.

MeetVerification은 matchId를 가지고 있고, postId는 Match를 통해 알 수 있다.

postId를 MeetVerification에 중복 저장하면 Match의 postId와 MeetVerification의 postId가 서로 달라질 수 있는 데이터 정합성 문제가 생길 수 있다.

그래서 postId는 호출부에서 MatchInfo를 통해 구한 뒤 파라미터로 전달한다.

---

## 4-7. getPostQrTokenOwner(Long postId)

### 역할

같은 Post에 이미 발급된 QR 토큰을 가진 MeetVerification을 찾는 private 헬퍼 메서드다.

### 책임

```
- postId 기준으로 형제 matchId 목록 조회
- matchId 목록으로 MeetVerification 벌크 조회
- QR 토큰이 null이 아닌 MeetVerification 하나를 반환
```

### 왜 token 문자열만 반환하지 않는가?

QR 토큰만 반환하면 만료 시각을 누구 기준으로 확인할지 애매하다.

따라서 QR 토큰을 가진 MeetVerification 자체를 반환하고, 해당 객체의 qrToken과 qrExpiresAt을 함께 사용한다.

---

## 4-8. findPostQrTokenOwner(List meetVerifications)

### 역할

이미 조회된 MeetVerification 목록에서 QR 토큰을 가진 항목을 찾는 private 헬퍼 메서드다.

### 책임

```
- MeetVerification 목록 순회
- qrToken이 null이 아닌 항목을 하나 반환
- 없으면 null 반환
```

### 왜 분리했는가?

getMeetQrByPost()에서는 이미 siblingMvList를 조회한 상태다.

그런데 다시 getPostQrTokenOwner(postId)를 호출하면 같은 목록을 한 번 더 DB에서 조회하게 된다.

그래서 이미 조회한 목록이 있을 때는 findPostQrTokenOwner()로 메모리에서 바로 찾도록 분리했다.

---

## 4-9. propagateAuthorVerification(Long postId)

### 역할

등록자의 GPS 장소 인증을 같은 Post의 모든 MeetVerification에 전파하는 private 헬퍼 메서드다.

### 책임

```
- postId 기준으로 형제 matchId 목록 조회
- MeetVerification을 findAllByMatchIdIn()으로 벌크 조회
- 이미 등록자 인증된 항목은 스킵
- 나머지 MeetVerification에 authorPlaceVerifiedAt 기록
```

### 왜 필요한가?

그룹 만남에서 등록자는 한 명이다.

등록자가 장소 인증을 한 번 했으면, 해당 Post에 연결된 모든 신청자 Match에서 등록자 인증은 완료된 것으로 봐야 한다.

그렇지 않으면 신청자 B의 MeetVerification에는 authorPlaceVerifiedAt이 null로 남고, 노쇼 스케줄러가 등록자를 HOST_NO_SHOW로 잘못 판단할 수 있다.

### 성능 개선

기존에는 형제 matchId를 순회하면서 findByMatchId()를 반복 호출할 수 있었다.

수정 후에는 findAllByMatchIdIn()으로 한 번에 조회해서 N+1 문제를 줄인다.

---

## 4-10. QrResponseDto.ofSharedToken(...)

### 역할

Post 공통 QR 응답을 생성하는 정적 팩토리 메서드다.

### 책임

```
- postId
- qrToken
- qrExpiresAt
```

위 정보를 담은 QR 응답 DTO를 생성한다.

### 왜 postId를 내려주는가?

등록자 QR은 특정 matchId의 QR이 아니라 Post 단위 공통 QR이다.

따라서 응답에서도 matchId보다 postId를 사용하는 편이 의미상 더 정확하다.

---

## 5. 1:1 만남에서의 동작

1:1 만남에서는 Post에 Match가 하나만 있다.

따라서 수정된 구조에서도 정상적으로 동작한다.

```
1. 등록자 GPS 인증
2. 신청자 GPS 인증
3. Post 공통 QR 발급
4. 신청자 QR 스캔
5. 해당 Match COMPLETED
6. 남은 MATCHED 없음
7. Post COMPLETED
8. 등록자 책임비 환급
9. 채팅방 비활성 예약
```

즉, 1:1에서는 기존과 결과가 거의 동일하다.

다만 내부 구조가 더 안전하게 분리된 것이다.

---

## 6. 그룹 만남에서의 동작

그룹 만남에서는 Post에 여러 Match가 있다.

```
Post 1
 ├─ Match A
 ├─ Match B
 └─ Match C
```

신청자 A가 먼저 QR을 스캔하면 다음과 같이 처리된다.

```
A MeetVerification → DONE
A Match → COMPLETED
A 신청자 예치금 환급
B, C Match → MATCHED 유지
Post → 아직 COMPLETED 아님
등록자 책임비 → 아직 환급 안 함
채팅방 비활성 예약 → 아직 안 함
```

신청자 B가 스캔하면 다음과 같다.

```
B MeetVerification → DONE
B Match → COMPLETED
C Match → MATCHED 유지
Post → 아직 COMPLETED 아님
```

마지막으로 신청자 C가 스캔하면 다음과 같다.

```
C MeetVerification → DONE
C Match → COMPLETED
남은 MATCHED Match 없음
Post → COMPLETED
등록자 책임비 환급
채팅방 비활성 예약
```

이렇게 하면 그룹 매칭에서도 각 신청자의 QR 스캔 여부를 개별적으로 관리하면서, 등록자는 하나의 QR만 보여줄 수 있다.

---

## 7. 책임 분리 정리

이번 수정의 핵심은 도메인 책임을 분리한 것이다.

### Meet 도메인

```
- GPS 인증
- QR 토큰 발급/조회/검증
- MeetVerification 상태 변경
- 신청자별 QR 스캔 여부 관리
- 위치 데이터 삭제
```

### Match 도메인

```
- Match 단건 완료
- 신청자 예치금 환급
- 모든 Match 종료 여부 판단
- Post 최종 완료 요청
- 등록자 책임비 환급
```

### Post 도메인

```
- Post 조회
- Post 상태 변경
- PESSIMISTIC_WRITE 락 조회 제공
```

### Chat 도메인

```
- 모든 신청자의 만남 인증이 끝난 뒤 채팅방 비활성 예약
```

---

## 8. 최종 요약

이번 수정은 다음 한 문장으로 요약할 수 있다.

```
등록자 QR은 Post 단위로 1개만 발급하고,
신청자 QR 스캔 완료 여부는 Match / MeetVerification 단위로 각각 관리하도록 분리했다.
```

이 구조를 통해 다음 문제가 해결된다.

```
- 그룹 매칭에서 신청자별 QR 토큰이 달라지는 문제
- 첫 번째 신청자 QR 스캔만으로 Post 전체가 완료되는 문제
- 두 번째 이후 신청자가 QR 스캔에 실패하는 문제
- 등록자 책임비가 너무 일찍 환급되는 문제
- 채팅방 비활성 예약이 너무 일찍 실행되는 문제
- 등록자 GPS 인증이 일부 MeetVerification에 전파되지 않아 HOST_NO_SHOW로 오판되는 문제
```

결과적으로 1:1 만남과 그룹 만남 모두 동일한 QR 발급 구조를 사용하면서도, 신청자별 만남 인증 상태는 독립적으로 관리할 수 있게 된다.

---

</details>

---

<a id="그룹-매칭에서-첫-번째-qr-스캔-후-나머지-신청자가-인증하지-못하는-문제"></a>

<details>
<summary><strong>그룹 매칭에서 첫 번째 QR 스캔 후 나머지 신청자가 인증하지 못하는 문제</strong></summary>

## 그룹 매칭에서 첫 번째 QR 스캔 후 나머지 신청자가 인증하지 못하는 문제

## 문제 상황

프로젝트의 만남 인증 기능은 처음에 1:1 만남을 기준으로 구현되어 있었다.

1:1 만남에서는 하나의 Post에 하나의 Match만 존재한다.

```
Post 1개
 └─ Match 1개
```

이 구조에서는 신청자가 QR 스캔을 완료하면 바로 Post를 완료 처리해도 문제가 없다.

하지만 그룹 매칭에서는 하나의 Post에 여러 개의 Match가 연결된다.

```
Post 1개
 ├─ Match A
 ├─ Match B
 └─ Match C
```

이때 기존 로직에서는 신청자 A가 QR을 스캔하는 순간 Post 전체가 완료 처리될 수 있었다.

그 결과 신청자 B, C는 아직 QR을 스캔하지 않았는데도 Post가 이미 COMPLETED 상태가 되어 이후 인증 흐름이 깨지는 문제가 발생할 수 있었다.

---

## 증상

그룹 매칭 상황에서 다음과 같은 문제가 발생할 수 있었다.

```
1. 등록자와 신청자 A가 장소 인증 완료
2. 신청자 A가 QR 스캔
3. 서버에서 Match 완료와 Post 완료를 동시에 처리
4. Post가 COMPLETED 상태가 됨
5. 신청자 B가 이후 QR 스캔 시도
6. 이미 Post가 완료된 상태라 정상 인증 흐름이 깨짐
```

또한 신청자별로 QR 토큰이 다르게 발급되는 문제도 있었다.

기존에는 각 MeetVerification마다 QR 토큰을 새로 생성했다.

```
A의 MeetVerification → hp_qr_aaa
B의 MeetVerification → hp_qr_bbb
C의 MeetVerification → hp_qr_ccc
```

하지만 실제 서비스에서 등록자는 QR을 하나만 보여준다.

그룹 만남에서 신청자별 토큰이 달라지면, 등록자가 보여주는 QR과 신청자가 검증해야 하는 QR이 불일치할 수 있다.

---

## 원인 분석

원인은 크게 두 가지였다.

---

## 원인 1. QR 토큰 발급 단위가 Match / MeetVerification 기준이었다

기존 QR 발급 로직은 MeetVerification마다 UUID를 생성하는 방식이었다.

즉, 하나의 Post에 여러 Match가 있으면 각 Match의 MeetVerification마다 서로 다른 QR 토큰이 저장될 수 있었다.

하지만 QR은 등록자가 화면에 보여주는 인증 수단이다.

등록자는 그룹 만남에서도 QR을 하나만 보여줘야 한다.

따라서 QR 토큰의 발급 단위는 Match가 아니라 Post가 되어야 했다.

---

## 원인 2. QR 스캔 성공 시 Match 완료와 Post 완료를 한 번에 처리했다

기존 QR 스캔 성공 로직은 다음과 같은 흐름이었다.

```
QR 스캔 성공
→ MeetVerification DONE
→ Match COMPLETED
→ Post COMPLETED
→ 등록자 책임비 환급
→ 채팅방 비활성 예약
```

1:1에서는 이 흐름이 문제가 없었다.

하지만 그룹에서는 신청자 한 명의 QR 스캔 성공이 Post 전체 완료를 의미하지 않는다.

그룹에서는 다음처럼 의미를 분리해야 했다.

```
신청자 한 명의 QR 스캔 성공
→ 해당 Match만 완료

모든 신청자의 QR 스캔 완료
→ Post 전체 완료
```

기존 로직은 이 두 단위를 분리하지 못하고 있었다.

---

## 해결 방향

해결 방향은 다음과 같이 정리했다.

```
QR 토큰은 Post 기준으로 1개만 발급한다.
QR 스캔 완료 여부는 신청자별 Match 기준으로 관리한다.
Post 완료는 모든 Match가 종료된 뒤 한 번만 처리한다.
```

---

## 해결 1. 등록자 QR 조회 API를 postId 기준으로 변경

기존 QR 조회는 matchId 기준이었다.

```
GET /api/v1/matches/{matchId}/qr
```

하지만 그룹 매칭에서 등록자가 어떤 신청자의 matchId로 QR을 조회해야 하는지 애매했다.

QR은 특정 Match에 종속된 자원이 아니라 Post에 종속된 자원이므로 API를 다음과 같이 변경했다.

```
GET /api/v1/posts/{postId}/qr
```

서비스 메서드도 다음과 같이 변경했다.

```java
QrResponseDto getMeetQrByPost(Long userId, Long postId);
```

이제 등록자는 postId 기준으로 공통 QR을 조회한다.

---

## 해결 2. QR 응답 DTO를 postId 기준으로 변경

QR 응답 DTO도 다음과 같이 정리했다.

```java
public record QrResponseDto(
        Long postId,
        String qrToken,
        LocalDateTime qrExpiresAt
) {
    public static QrResponseDto of(
            Long postId,
            String qrToken,
            LocalDateTime qrExpiresAt
    ) {
        return new QrResponseDto(postId, qrToken, qrExpiresAt);
    }
}
```

이제 응답에서 matchId가 아니라 postId를 내려준다.

이유는 등록자 QR이 특정 신청자 Match의 QR이 아니라 Post 공통 QR이기 때문이다.

---

## 해결 3. Post 기준 공통 QR 토큰 사용

QR 토큰 발급 로직을 다음 방식으로 변경했다.

```
1. 같은 postId에 이미 발급된 QR 토큰이 있는지 확인
2. 있으면 기존 토큰 재사용
3. 없으면 새 QR 토큰 생성
4. 현재 MeetVerification에 저장
```

핵심 메서드는 다음과 같다.

```java
private void issueQrTokenIfNeeded(MeetVerification meetVerification, Long postId)
```

여기서 MeetVerification에 postId 필드를 추가하지 않았다.

postId는 Match를 통해 알 수 있기 때문이다.

MeetVerification에 postId를 중복 저장하면 다음과 같은 위험이 있다.

```
Match.postId = 1
MeetVerification.postId = 2
```

이런 식의 데이터 불일치가 생길 수 있기 때문에, postId는 호출부에서 MatchInfo를 통해 구해서 파라미터로 넘기도록 했다.

---

## 해결 4. QR 스캔 검증은 Post 공통 QR 토큰 기준으로 수행

QR 스캔 API는 기존처럼 matchId 기준을 유지했다.

```
POST /api/v1/matches/{matchId}/qr/scan
```

신청자가 QR을 스캔할 때는 본인의 Match를 알아야 하기 때문이다.

다만 QR 토큰 검증은 본인의 MeetVerification 토큰이 아니라, Post 기준 공통 QR 토큰과 비교하도록 변경했다.

```
요청 qrToken
→ Post 공통 QR 토큰과 비교
```

검증 흐름은 다음과 같다.

```
1. matchId로 신청자 본인의 MeetVerification 조회
2. 요청자가 해당 Match의 신청자인지 확인
3. MeetVerification 상태가 VERIFIED인지 확인
4. Post 공통 QR 토큰 조회
5. 요청 qrToken과 Post 공통 QR 토큰 비교
6. 일치하면 본인의 MeetVerification만 DONE 처리
```

---

## 해결 5. Match 단건 완료 메서드 추가

기존 completeMatch()는 Match 완료와 Post 완료를 한 번에 처리했다.

그래서 QR 정상 완료 경로에서는 사용하지 않도록 하고, Match 단건만 완료하는 메서드를 추가했다.

```java
boolean completeSingleMatch(Long matchId);
```

이 메서드는 다음 역할을 한다.

```
- Match를 PESSIMISTIC_WRITE 락으로 조회
- 이미 COMPLETED면 중복 스캔으로 보고 return false
- MATCHED 상태인지 확인
- Match를 COMPLETED 처리
- 신청자 예치금 환급
- 후기 마감 알림 예약
- 같은 postId에 남은 MATCHED Match 개수 확인
- 남은 MATCHED가 없으면 true 반환
```

동일 Match에 대해 QR 스캔이 동시에 들어오는 경우 중복 완료와 중복 환급을 막기 위해 Match 조회 시 PESSIMISTIC_WRITE 락을 사용했다.

---

## 해결 6. Post 최종 완료 메서드 추가

Post 전체 완료는 모든 활성 Match가 종료된 뒤에만 처리해야 한다.

이를 위해 다음 메서드를 추가했다.

```java
void completePostIfAllMatchesCompleted(Long postId);
```

이 메서드는 다음 역할을 한다.

```
- 같은 postId에 MATCHED 상태의 Match가 남아 있는지 확인
- 남아 있으면 아무 작업도 하지 않음
- Post를 PESSIMISTIC_WRITE 락으로 조회
- 이미 COMPLETED면 아무 작업도 하지 않음
- Post COMPLETED 처리
- 등록자 책임비 환급
```

Post 조회에는 PostService의 getPostByIdWithLock()을 사용했다.

이 메서드는 Post row에 PESSIMISTIC_WRITE 락을 걸기 때문에, 동시에 마지막 QR 스캔이 들어오더라도 등록자 책임비가 중복 환급될 가능성을 줄일 수 있다.

---

## 해결 7. 등록자 GPS 인증 전파 및 N+1 개선

그룹 만남에서는 등록자가 한 명이므로, 등록자가 장소 인증을 한 번 완료하면 같은 Post에 속한 모든 Match에 등록자 인증이 반영되어야 한다.

이를 위해 다음 메서드를 개선했다.

```java
private void propagateAuthorVerification(Long postId)
```

기존에는 형제 Match들을 순회하면서 MeetVerification을 하나씩 조회할 수 있었다.

이 방식은 Match 수만큼 쿼리가 발생하는 N+1 문제가 있었다.

수정 후에는 findAllByMatchIdIn()으로 MeetVerification을 한 번에 조회하도록 변경했다.

```
기존
→ matchId마다 findByMatchId() 반복 호출

수정
→ findAllByMatchIdIn() 한 번 호출
```

이렇게 해서 등록자 장소 인증 전파와 성능 문제를 함께 개선했다.

---

## 결과

수정 후 그룹 만남의 흐름은 다음과 같다.

```
등록자 GPS 인증
→ 모든 MeetVerification에 등록자 인증 전파

신청자 A GPS 인증
→ A의 MeetVerification VERIFIED
→ Post 공통 QR 토큰 발급

등록자 QR 조회
→ postId 기준 공통 QR 반환

신청자 A QR 스캔
→ A의 MeetVerification DONE
→ A의 Match COMPLETED
→ A 신청자 예치금 환급
→ 아직 B, C가 MATCHED라면 Post 유지

신청자 B QR 스캔
→ B의 MeetVerification DONE
→ B의 Match COMPLETED
→ 아직 C가 MATCHED라면 Post 유지

신청자 C QR 스캔
→ C의 MeetVerification DONE
→ C의 Match COMPLETED
→ 남은 MATCHED 없음
→ Post COMPLETED
→ 등록자 책임비 환급
→ 채팅방 비활성 예약
```

---

## 배운 점

이번 트러블슈팅을 통해 가장 크게 배운 점은 “도메인 이벤트의 완료 단위”를 정확히 나눠야 한다는 것이다.

처음에는 QR 스캔 성공을 곧바로 만남 전체 완료로 봤다.

하지만 그룹 매칭에서는 그렇지 않았다.

```
신청자 한 명의 QR 스캔 성공
→ 해당 Match 완료

모든 신청자의 QR 스캔 성공
→ Post 완료
```

즉, 동일한 이벤트처럼 보여도 도메인 구조가 1:1인지 그룹인지에 따라 완료의 의미가 달라진다.

또 API 설계에서도 자원의 기준을 명확히 잡아야 한다는 점을 배웠다.

등록자 QR은 Match 자원이 아니라 Post 자원이다.

따라서 QR 조회 API는 matchId가 아니라 postId 기준으로 설계하는 것이 더 자연스럽다.

---

## 남은 과제

정상 QR 인증 완료 경로는 이번 작업으로 그룹 매칭 구조에 맞게 개선했다.

다만 노쇼/이의제기 경로는 아직 그룹 정책을 더 검토해야 한다.

예를 들어 신청자 한 명만 노쇼일 때 Post 전체를 완료할지, 해당 Match만 노쇼 처리할지 같은 정책은 더 명확히 정리할 필요가 있다.

이번 작업에서는 정상 QR 완료 흐름을 우선 수정했고, 노쇼/이의제기 그룹 정책은 추후 별도 리팩토링 대상으로 남겨두었다.

---

</details>

---

<a id="노쇼-이의제기-매칭-선택-방식-변경-트러블슈팅"></a>

<details>
<summary><strong>노쇼 이의제기 매칭 선택 방식 변경 트러블슈팅</strong></summary>

## 노쇼 이의제기 매칭 선택 방식 변경 트러블슈팅

## 문제 상황

고객센터의 `노쇼 이의제기` 화면에서 사용자가 직접 “이의제기 매칭” 목록을 선택하는 방식으로 구현되어 있었다.

하지만 정책이 변경되면서 사용자가 노쇼 매칭을 직접 선택하면 안 되고, 매칭 상세 화면 또는 알림을 통해 진입할 때 전달되는 `matchId`를 그대로 사용해야 하는 요구사항이 생겼다.

즉, 사용자는 별도 매칭 목록을 고르는 것이 아니라 이미 연결된 매칭에 대해 이의제기를 접수해야 한다.

## 기존 문제점

기존 프론트 로직은 다음과 같았다.

- 노쇼 상태의 매칭 목록을 프론트에서 조회함
- 조회된 목록 중 하나를 사용자가 선택함
- 선택된 `selectedNoShowMatchId`를 기준으로 이의제기 API를 호출함
- 제출 버튼 활성화 조건도 `selectedNoShowMatchId` 존재 여부에 의존함

이 방식은 다음 정책과 맞지 않았다.

- 사용자가 이의제기 대상 매칭을 직접 선택하면 안 됨
- 매칭 상세에서 진입한 경우 URL의 `matchId`를 신뢰해야 함
- `matchId`가 없는 진입은 이의제기 접수가 불가능해야 함

## 원인

프론트에서 이의제기 대상 매칭을 자체적으로 결정하려고 한 것이 문제였다.

백엔드 API는 이미 다음 형태로 `matchId`를 경로 파라미터로 받고 있었다.

```text
POST /api/v1/matches/{matchId}/disputes
```

따라서 프론트에서는 별도 목록 조회나 선택 상태를 관리할 필요 없이, URL 쿼리 파라미터의 `matchId`만 검증해서 그대로 사용하면 됐다.

## 수정 방향

`InquiryCenterPage.tsx`에서 노쇼 이의제기 흐름을 다음 방식으로 변경했다.

1. URL에서 `matchId` 쿼리 파라미터를 읽는다.
2. `matchId`가 양의 정수인지 검증한다.
3. “이의제기 매칭” 영역은 선택 UI가 아니라 읽기전용 텍스트로 표시한다.
4. 제출 시 URL에서 읽은 `matchId`를 그대로 사용한다.
5. 제출 버튼은 `matchId`가 유효하고 상세 사유가 입력된 경우에만 활성화한다.

## 주요 변경 내용

### 1. 매칭 목록 조회 제거

기존에는 `getMyMatches`로 노쇼 매칭 목록을 조회하고 있었다.

```ts
getMyMatches(status, 0, 20)
```

수정 후에는 이 로직을 제거했다.

이제 노쇼 이의제기 화면에서는 매칭 목록을 프론트에서 불러오지 않는다.

### 2. 선택 상태 제거

기존에는 사용자가 선택한 매칭 ID를 별도 상태로 관리했다.

```ts
selectedNoShowMatchId
```

수정 후에는 이 상태를 제거하고, URL의 `matchId`만 사용하도록 변경했다.

### 3. URL matchId 필수 처리

```ts
const requestedNoShowMatchId = searchParams.get('matchId');
const requestedNoShowMatchIdNumber = Number(requestedNoShowMatchId);
const hasRequestedNoShowMatchId =
  Number.isInteger(requestedNoShowMatchIdNumber) &&
  requestedNoShowMatchIdNumber > 0;
```

`matchId`가 없거나 올바른 숫자가 아니면 이의제기를 접수할 수 없도록 처리했다.

### 4. 읽기전용 매칭 표시

기존 선택 박스를 제거하고, 다음과 같이 읽기전용으로 표시했다.

```tsx
{hasRequestedNoShowMatchId
  ? `연결된 매칭 #${requestedNoShowMatchId}`
  : '연결된 매칭 정보가 없습니다.'}
```

사용자는 매칭을 직접 선택하지 않고, 현재 연결된 매칭만 확인할 수 있다.

### 5. 제출 API 호출 변경

기존에는 선택된 매칭 ID를 사용했다.

```ts
await createDispute(Number(selectedNoShowMatchId), {
  disputeType,
  reason: disputeReason.trim(),
});
```

수정 후에는 URL에서 가져온 `matchId`를 그대로 사용한다.

```ts
await createDispute(requestedNoShowMatchIdNumber, {
  disputeType,
  reason: disputeReason.trim(),
});
```

### 6. 제출 버튼 활성화 조건 변경

기존에는 매칭 목록 또는 선택된 매칭 여부에 의존했다.

수정 후에는 다음 조건을 사용한다.

```tsx
disabled={
  submitting ||
  !hasRequestedNoShowMatchId ||
  !disputeReason.trim()
}
```

즉, 제출 버튼은 다음 조건을 만족해야 활성화된다.

- 제출 중이 아님
- URL의 `matchId`가 유효함
- 상세 사유가 입력됨

## 최종 결과

수정 후 노쇼 이의제기 흐름은 다음과 같이 정리됐다.

1. 사용자가 매칭 상세 화면에서 `노쇼 이의제기` 버튼 클릭
2. `/me/inquiries?view=noShow&matchId={matchId}`로 이동
3. 고객센터 화면에서 `연결된 매칭 #{matchId}`를 읽기전용으로 표시
4. 사용자가 이의제기 사유와 상세 사유 입력
5. 제출 시 `POST /api/v1/matches/{matchId}/disputes` 호출

## 주의 사항

이번 작업에서는 백엔드 코드를 수정하지 않았다.

백엔드 API는 이미 `matchId`를 path variable로 받아 이의제기를 생성하는 구조였기 때문에, 프론트에서 `matchId`를 올바르게 전달하도록 정리하는 작업만 진행했다.

## 정리

이번 이슈의 핵심은 “사용자가 직접 이의제기 매칭을 고르는 방식”이 아니라 “이미 연결된 matchId를 기반으로 이의제기를 접수하는 방식”으로 흐름을 바꾸는 것이었다.

따라서 프론트에서는 매칭 목록 조회와 선택 상태를 제거하고, URL의 `matchId`를 필수값으로 사용하도록 수정했다.

</details>
