# API 명세서

## 기본 정보

| 항목 | 내용 |
| --- | --- |
| Base URL | `/api/v1` |
| Content-Type | `application/json` |
| 사용자 인증 | `Authorization: Bearer {accessToken}` |
| 관리자 인증 | `Authorization: Bearer {adminAccessToken}` |
| 실시간 응답 | SSE: `text/event-stream` |
| WebSocket | STOMP over WebSocket, handshake `/ws/chat` |

## 공통 응답 형식

모든 JSON API는 기본적으로 `ApiResponseDto<T>` 형식으로 응답합니다. 실제 응답 바디는 `data`에 각 응답 DTO가 들어가는 구조입니다.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

실패 응답은 다음 형식입니다.

```json
{
  "success": false,
  "code": "AUTH_006",
  "message": "유효하지 않거나 만료된 토큰입니다.",
  "data": null
}
```

## 엔드포인트별 요청/응답 JSON 예시

각 예시는 필드 누락 없이 구조를 보여주기 위한 샘플입니다. 실제 값은 실행 시점과 데이터에 따라 달라집니다.

<details>
<summary><code>POST /api/v1/auth/email/otp</code> - 이메일 OTP 발송</summary>

학교 이메일로 6자리 OTP를 발송합니다. 등록된 `.ac.kr` 학교 도메인만 허용하며, 재발송은 1분 쿨다운·24시간 최대 5회 제한이 적용됩니다.

**Request Body**

```json
{
  "email": "student@university.ac.kr"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 등록된 대학의 학교 이메일 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "expireSeconds": 300
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/email/otp/verify</code> - 이메일 OTP 검증</summary>

OTP를 검증하고 회원가입 전용 `signup_token` HttpOnly 쿠키를 발급합니다. 토큰은 15분간 유효하고 `/api/v1/auth/signup` 요청에만 전송됩니다. OTP는 최대 5회까지 검증할 수 있습니다.

**Request Body**

```json
{
  "email": "student@university.ac.kr",
  "otpCode": "123456"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | OTP를 발송한 학교 이메일 |
| `otpCode` | string | Y | 6자리 숫자 인증 코드 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "universityId": 1,
    "universityName": "홍길동"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/signup</code> - 회원가입</summary>

OTP 검증으로 발급된 `signup_token` 쿠키를 포함해 회원가입을 완료합니다. 가입 시 10,000P가 지급되고, 응답에는 Access Token, 쿠키에는 `refresh_token`과 `device_id`가 발급됩니다.

**Request Body**

```json
{
  "password": "Password123!",
  "name": "홍길동",
  "nickname": "한끼친구",
  "major": "컴퓨터공학과",
  "studentNumber": "20241234",
  "birthDate": "2001-01-01",
  "gender": "MALE",
  "termAgreements": [
    {
      "termVersion": "string",
      "agreed": true
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `password` | string | Y | 비밀번호. 8~20자 |
| `name` | string | Y | 이름. 최대 50자 |
| `nickname` | string | Y | 닉네임. 2~30자 |
| `major` | string | Y | 학과. 최대 100자 |
| `studentNumber` | string | Y | 학번. 최대 20자 |
| `birthDate` | date | Y | 생년월일 |
| `gender` | string | Y | `MALE` 또는 `FEMALE` |
| `termAgreements` | array | Y | 약관 버전과 동의 여부 목록. 필수 약관은 모두 동의해야 함 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "nickname": "한끼친구",
    "point": 10000,
    "accessToken": "sample-token"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/login</code> - 로그인</summary>

이메일과 비밀번호로 로그인합니다. Access Token은 응답 본문에, `refresh_token`과 `device_id`는 HttpOnly 쿠키로 발급됩니다. 정지·탈퇴 계정은 로그인할 수 없습니다.

**Request Body**

```json
{
  "email": "student@university.ac.kr",
  "password": "Password123!"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 가입한 이메일 |
| `password` | string | Y | 비밀번호 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "nickname": "한끼친구",
    "accessToken": "sample-token"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/refresh</code> - 토큰 재발급</summary>

`refresh_token`과 `device_id` 쿠키를 사용해 Access Token을 재발급합니다. 성공 시 Refresh Token도 함께 교체됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "accessToken": "sample-token"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/logout</code> - 로그아웃</summary>

`refresh_token`과 `device_id` 쿠키를 기준으로 서버의 Refresh Token을 삭제하고 두 쿠키를 만료시킵니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null
}
```

</details>

<details>
<summary><code>GET /api/v1/users/me</code> - 내 정보 조회</summary>

로그인 사용자의 프로필, 포인트 잔액, 매너온도와 학교 정보를 조회합니다. 정지(`SUSPENDED`) 계정도 이 API는 사용할 수 있습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "email": "student@university.ac.kr",
    "name": "홍길동",
    "nickname": "한끼친구",
    "universityId": 1,
    "major": "컴퓨터공학과",
    "studentNumber": "20241234",
    "birthDate": "2001-01-01",
    "gender": "MALE",
    "point": 1000,
    "mannerTemperature": 36.5,
    "status": "ACTIVE",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/users/me</code> - 내 정보 수정</summary>

닉네임·학과·비밀번호를 부분 수정합니다. 세 필드 중 하나 이상은 포함해야 하며, 비밀번호를 바꾸는 경우에만 `currentPassword`가 필요합니다.

**Request Body**

```json
{
  "currentPassword": "Password123!",
  "newPassword": "Password123!",
  "nickname": "한끼친구",
  "major": "컴퓨터공학과"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `currentPassword` | string | 조건부 | `newPassword` 변경 시 현재 비밀번호 |
| `newPassword` | string | N | 새 비밀번호. 8~20자, 기존 비밀번호와 달라야 함 |
| `nickname` | string | N | 2~30자. 한글·영문·숫자만 사용, 중복 불가 |
| `major` | string | N | 최대 100자. 한글·영문·숫자·공백만 사용 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "nickname": "한끼친구",
    "major": "컴퓨터공학과",
    "passwordChanged": true,
    "updatedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>DELETE /api/v1/users/me</code> - 회원 탈퇴</summary>

현재 비밀번호를 확인한 뒤 계정을 `WITHDRAWN` 상태로 변경합니다. 탈퇴 처리 과정에서 Refresh Token이 삭제되고 관련 쿠키가 만료됩니다. 정지 기간이 남아 있는 계정과 이미 탈퇴한 계정은 탈퇴할 수 없습니다.

**Request Body**

```json
{
  "password": "Password123!"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `password` | string | Y | 현재 비밀번호 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "withdrawnAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/universities</code> - 대학 목록 조회</summary>

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "universityId": 1,
      "universityName": "홍길동",
      "eDomain": "string"
    }
  ]
}
```

</details>

<details>
<summary><code>POST /api/v1/posts</code> - 게시글 작성</summary>

새 식사팟 게시글을 작성합니다. 작성 시 책임비가 포인트 잔액에서 예치되며, 책임비는 최소 200P·100P 단위여야 합니다. 만남 시간은 현재 이후여야 하고 최대 참여 인원은 등록자를 포함해 2~10명입니다.

**Request Body**

```json
{
  "meetAt": "2026-06-22T12:30:00",
  "placeName": "홍길동",
  "placeLat": 37.5665,
  "placeLng": 126.978,
  "content": "요청 내용입니다",
  "authorDeposit": 1000,
  "maxApplicants": 2
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `meetAt` | datetime | Y | 현재 이후의 만남 희망 시간 |
| `placeName` | string | Y | 장소명. 최대 200자 |
| `placeLat` | number | Y | 위도. -90~90 |
| `placeLng` | number | Y | 경도. -180~180 |
| `content` | string | N | 게시글 내용. 최대 500자 |
| `authorDeposit` | number | Y | 책임비. 최소 200P, 100P 단위 |
| `maxApplicants` | number | Y | 등록자 포함 최대 참여 인원. 2~10 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "authorId": 1,
    "authorNickname": "한끼친구",
    "meetAt": "2026-06-22T12:30:00",
    "placeName": "홍길동",
    "placeLat": 37.5665,
    "placeLng": 126.978,
    "content": "요청 내용입니다",
    "authorDeposit": 1000,
    "status": "OPEN",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/posts</code> - 게시글 목록 조회</summary>

로그인 사용자의 같은 학교 게시글을 조회합니다. 기본적으로 모집 중인 `OPEN` 게시글을 책임비 높은 순으로 반환합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `status` | N | `OPEN` | `OPEN`, `MATCHED`, `COMPLETED`, `CANCELLED`, `EXPIRED` 중 하나 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기. 최대 `50` |
| `sort` | N | `DEPOSIT_DESC` | `DEPOSIT_DESC`(책임비 높은 순), `LATEST`(최신순), `MEET_AT_ASC`(만남 시간 빠른 순) |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "postId": 1,
        "authorId": 1,
        "authorNickname": "한끼친구",
        "authorMajor": "컴퓨터공학과",
        "authorStudentNumber": "20241234",
        "authorMannerTemperature": 36.5,
        "meetAt": "2026-06-22T12:30:00",
        "placeName": "홍길동",
        "authorDeposit": 1000,
        "currentApplicants": 1,
        "maxApplicants": 2,
        "status": "OPEN",
        "createAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/posts/{postId}</code> - 게시글 상세 조회</summary>

같은 학교 게시글의 상세를 조회합니다. `isMine`은 현재 로그인 사용자가 작성자인지 여부입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "authorId": 1,
    "authorNickname": "한끼친구",
    "authorMajor": "컴퓨터공학과",
    "authorStudentNumber": "20241234",
    "authorMannerTemperature": 36.5,
    "meetAt": "2026-06-22T12:30:00",
    "placeName": "홍길동",
    "placeLat": 37.5665,
    "placeLng": 126.978,
    "content": "요청 내용입니다",
    "authorDeposit": 1000,
    "currentApplicants": 1,
    "maxApplicants": 2,
    "status": "OPEN",
    "isMine": true,
    "createAt": "2026-06-22T12:30:00",
    "updateAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/posts/{postId}</code> - 게시글 수정</summary>

작성자만 `OPEN` 상태 게시글을 부분 수정할 수 있습니다. 변경하지 않을 필드는 생략할 수 있으며, 책임비를 올리면 차액이 추가 예치되고 내리면 차액이 반환됩니다.

**Request Body**

```json
{
  "meetAt": "2026-06-22T12:30:00",
  "placeName": "홍길동",
  "placeLat": 37.5665,
  "placeLng": 126.978,
  "content": "요청 내용입니다",
  "authorDeposit": 1200
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `meetAt` | datetime | N | 현재 이후의 만남 희망 시간 |
| `placeName` | string | N | 장소명. 최대 200자 |
| `placeLat` | number | N | 위도. -90~90 |
| `placeLng` | number | N | 경도. -180~180 |
| `content` | string | N | 게시글 내용. 최대 500자 |
| `authorDeposit` | number | N | 변경할 책임비. 최소 200P, 100P 단위 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "meetAt": "2026-06-22T12:30:00",
    "placeName": "홍길동",
    "authorDeposit": 1200,
    "status": "OPEN",
    "updatedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>DELETE /api/v1/posts/{postId}</code> - 게시글 삭제</summary>

작성자만 `OPEN` 상태 게시글을 삭제할 수 있습니다. 삭제하면 예치된 책임비가 전액 반환되고 게시글 상태는 `CANCELLED`로 변경됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "refundedPoint": 1000
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/posts/{postId}/delete-reason</code> - 삭제 사유 조회</summary>

관리자가 강제 삭제한 본인 게시글의 삭제 사유를 조회합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "placeName": "홍길동",
    "deleteReason": "상세 사유입니다",
    "deletedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/posts/{postId}/matches</code> - 매칭 신청</summary>

로그인 사용자가 모집 중인 게시글에 선착순으로 신청합니다. 본인 게시글·모집 종료 게시글·이미 신청한 게시글에는 신청할 수 없으며, 신청자도 게시글 등록자와 같은 예치 포인트를 보유해야 합니다. 신청이 완료되면 매칭이 생성되고 채팅방이 자동 생성됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "postId": 1,
    "authorId": 1,
    "authorNickname": "한끼친구",
    "applicantId": 1,
    "applicantNickname": "한끼친구",
    "authorDeposit": 1000,
    "applicantDeposit": 1000,
    "status": "MATCHED",
    "chatRoomId": 1,
    "matchedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}</code> - 매칭 상세 조회</summary>

매칭 등록자 또는 신청자만 조회할 수 있습니다. `status`는 `MATCHED`, `COMPLETED`, `CANCELLED`, `HOST_NO_SHOW`, `GUEST_NO_SHOW`, `BOTH_NO_SHOW`, `DISPUTED` 중 하나입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "postId": 1,
    "authorId": 1,
    "authorNickname": "한끼친구",
    "authorMajor": "컴퓨터공학과",
    "authorStudentNumber": "20241234",
    "applicantId": 1,
    "applicantNickname": "한끼친구",
    "applicantMajor": "컴퓨터공학과",
    "applicantStudentNumber": "20241234",
    "meetAt": "2026-06-22T12:30:00",
    "placeName": "홍길동",
    "placeLat": 37.5665,
    "placeLng": 126.978,
    "authorDeposit": 1000,
    "applicantDeposit": 1000,
    "currentApplicants": 1,
    "maxApplicants": 1,
    "authorMannerTemperature": 36.5,
    "participants": [
      {
        "userId": 1,
        "matchId": 1,
        "nickname": "한끼친구",
        "major": "컴퓨터공학과",
        "studentNumber": "20241234",
        "role": "APPLICANT",
        "status": "MATCHED",
        "matchedAt": "2026-06-22T12:30:00",
        "completedAt": "2026-06-22T12:30:00"
      }
    ],
    "status": "MATCHED",
    "chatRoomId": 1,
    "matchedAt": "2026-06-22T12:30:00",
    "completedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/me</code> - 내 매칭 목록 조회</summary>

로그인 사용자가 등록자 또는 신청자로 참여한 매칭을 생성일 최신순으로 조회합니다. `status`를 생략하면 전체 상태를 조회합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `status` | N | - | `MATCHED`, `COMPLETED`, `CANCELLED`, `HOST_NO_SHOW`, `GUEST_NO_SHOW`, `BOTH_NO_SHOW`, `DISPUTED` 중 하나 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기. 최대 `50` |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "matchId": 1,
        "postId": 1,
        "opponentId": 1,
        "opponentNickname": "한끼친구",
        "opponentMajor": "컴퓨터공학과",
        "opponentStudentNumber": "20241234",
        "meetAt": "2026-06-22T12:30:00",
        "placeName": "홍길동",
        "currentApplicants": 1,
        "maxApplicants": 1,
        "myDeposit": 1000,
        "isAuthor": true,
        "participants": [
          {
            "userId": 1,
            "matchId": 1,
            "nickname": "한끼친구",
            "major": "컴퓨터공학과",
            "studentNumber": "20241234",
            "role": "APPLICANT",
            "status": "MATCHED",
            "matchedAt": "2026-06-22T12:30:00",
            "completedAt": "2026-06-22T12:30:00"
          }
        ],
        "postStatus": "OPEN",
        "status": "MATCHED",
        "chatRoomId": 1,
        "matchedAt": "2026-06-22T12:30:00",
        "completedAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/matches/{matchId}/cancel</code> - 매칭 취소</summary>

매칭 당사자가 약속 시간 전 `MATCHED` 상태의 매칭을 취소합니다. 취소자는 자신의 예치 포인트 중 50%만 반환되고 나머지 50%는 몰수되며, 상대방 예치 포인트는 전액 반환됩니다. 취소 후 매칭 상태는 `CANCELLED`가 됩니다.

**Request Body**

```json
{
  "reason": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | N | 취소 사유. 최대 200자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "status": "CANCELLED",
    "refundedPoint": 500,
    "forfeitedPoint": 500
  }
}
```

</details>

<details>
<summary><code>PUT /api/v1/matches/{matchId}/location</code> - 위치 업데이트</summary>

GPS 인증 화면에서 매칭 당사자의 현재 위치를 갱신합니다. 프론트엔드는 5초 주기로 호출하며, 위치 정보는 Redis에 30초간 유지됩니다.

**Request Body**

```json
{
  "latitude": 37.5665,
  "longitude": 126.978
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `latitude` | number | Y | 현재 위도 |
| `longitude` | number | Y | 현재 경도 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "userId": 1,
    "latitude": 37.5665,
    "longitude": 126.978,
    "updatedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/location</code> - 위치 조회</summary>

GPS 인증 화면에서 본인과 상대방의 최근 위치를 조회합니다. 상대방이 위치를 아직 전송하지 않았거나 마지막 갱신 후 30초가 지나면 해당 위치는 `null`일 수 있습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "myLocation": {
      "latitude": 37.5665,
      "longitude": 126.978,
      "updatedAt": "2026-06-22T12:30:00",
      "role": "AUTHOR"
    },
    "opponentLocation": {
      "latitude": 37.5665,
      "longitude": 126.978,
      "updatedAt": "2026-06-22T12:30:00",
      "role": "APPLICANT"
    },
    "opponentLocations": [
      {
        "latitude": 37.5665,
        "longitude": 126.978,
        "updatedAt": "2026-06-22T12:30:00",
        "role": "APPLICANT"
      }
    ]
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/place-verification</code> - GPS 장소 인증</summary>

약속 시간 10분 전부터 10분 후까지 약속 장소 반경 250km 내에서 GPS 인증을 수행합니다. 양측 인증이 완료되면 상태가 `VERIFIED`가 되어 QR 인증 단계로 진행할 수 있습니다.

**Request Body**

```json
{
  "currentLat": 37.5665,
  "currentLng": 126.978
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `currentLat` | number | Y | 현재 위도. -90~90 |
| `currentLng` | number | Y | 현재 경도. -180~180 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "verificationStatus": "VERIFIED",
    "distanceMeters": 36.5,
    "authorPlaceVerifiedAt": "2026-06-22T12:30:00",
    "applicantPlaceVerifiedAt": "2026-06-22T12:30:00",
    "bothVerified": true
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/posts/{postId}/qr</code> - QR 조회</summary>

게시글 등록자만 QR 토큰을 조회할 수 있습니다. 모든 참여자의 GPS 인증이 완료됐거나 약속 시간에서 3분이 지난 뒤에 발급할 수 있으며, QR 토큰은 발급 시점부터 10분간 유효합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "qrToken": "sample-token",
    "qrExpiresAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/qr/scan</code> - QR 스캔</summary>

신청자가 등록자의 QR 토큰을 스캔해 만남 인증을 완료합니다. 성공 시 인증 상태는 `DONE`, 매칭 상태는 `COMPLETED`가 되며 예치 포인트가 반환됩니다.

**Request Body**

```json
{
  "qrToken": "sample-token"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `qrToken` | string | Y | 등록자가 발급받은 유효한 QR 토큰 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "verificationStatus": "DONE",
    "matchStatus": "COMPLETED",
    "completedAt": "2026-06-22T12:30:00",
    "refundedPoint": 1000
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/verification</code> - 만남 인증 상태 조회</summary>

매칭 당사자가 GPS·QR 인증 진행 상태와 참여자별 인증 현황을 조회합니다. `verificationStatus`는 `PENDING`, `VERIFIED`, `DONE`, 노쇼·이의제기 관련 상태 중 하나입니다. QR이 아직 발급되지 않은 경우 `qrExpiresAt`은 `null`입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "verificationStatus": "PENDING",
    "authorNickname": "한끼친구",
    "authorPlaceVerifiedAt": "2026-06-22T12:30:00",
    "participants": [
      {}
    ],
    "qrIssuedToAuthor": true,
    "qrExpiresAt": "2026-06-22T12:30:00",
    "completedAt": "2026-06-22T12:30:00",
    "noShowDecidedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/extension/request</code> - 시간 연장 요청</summary>

신청자만 약속 시간 5분 전까지 10분 연장을 요청할 수 있습니다. 요청 상태는 `REQUESTED`이며, 등록자는 요청 후 5분 안에 응답해야 합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "extensionStatus": "REQUESTED",
    "requesterId": 1,
    "requesterNickname": "한끼친구",
    "originalMeetAt": "2026-06-22T12:30:00",
    "expectedMeetAt": "2026-06-22T12:30:00",
    "requestedAt": "2026-06-22T12:30:00",
    "expiresAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/matches/{matchId}/extension/accept</code> - 시간 연장 수락</summary>

등록자만 대기 중인 연장 요청을 수락할 수 있습니다. 수락하면 상태가 `ACCEPTED`가 되고 약속 시간이 10분 연장됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "extensionStatus": "ACCEPTED",
    "originalMeetAt": "2026-06-22T12:30:00",
    "extendedMeetAt": "2026-06-22T12:30:00",
    "isExtended": true,
    "extendedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/matches/{matchId}/extension/reject</code> - 시간 연장 거절</summary>

등록자만 대기 중인 연장 요청을 거절할 수 있습니다. 거절 후 상태는 `REJECTED`가 되며 다시 요청할 수 있습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "extensionStatus": "REJECTED",
    "rejectedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/extension</code> - 시간 연장 상태 조회</summary>

매칭 당사자가 현재 연장 요청 상태를 조회합니다. 요청이 없는 `NONE` 상태에서는 요청자·요청 시각·만료 시각이 `null`입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "extensionStatus": "NONE",
    "requesterId": 1,
    "requesterNickname": "한끼친구",
    "isMyRequest": true,
    "originalMeetAt": "2026-06-22T12:30:00",
    "expectedMeetAt": "2026-06-22T12:30:00",
    "requestedAt": "2026-06-22T12:30:00",
    "expiresAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/reviews</code> - 후기 작성</summary>

`COMPLETED` 매칭의 신청자만 만남 완료 후 7일 이내에 등록자 후기를 한 번 작성할 수 있습니다. 후기는 수정·삭제할 수 없고, 작성하면 50P가 지급됩니다.

**Request Body**

```json
{
  "goodTags": [
    "ON_TIME"
  ],
  "badTags": []
}
```

`goodTags`와 `badTags`는 각각 최대 5개까지 선택할 수 있지만, 두 목록을 동시에 선택하거나 둘 다 비워둘 수는 없습니다. `DO_NOT_WANT_TO_MEET_AGAIN`을 선택하면 서로 다시 매칭되지 않도록 양방향 관계가 생성됩니다.

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `goodTags` | array | 조건부 | `ON_TIME`, `KIND`, `GOOD_COMMUNICATION`, `CLEAN_MANNER`, `WANT_MEET_AGAIN` |
| `badTags` | array | 조건부 | `LATE`, `NO_REPLY`, `UNCOMFORTABLE`, `BAD_MANNER`, `DO_NOT_WANT_TO_MEET_AGAIN` |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reviewId": 1,
    "matchId": 1,
    "targetId": 1,
    "targetNickname": "한끼친구",
    "goodTags": [
      "ON_TIME"
    ],
    "badTags": [],
    "tagScoreDelta": 1,
    "doNotWantToMeetAgainSelected": false,
    "rewardPoint": 50,
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/me/reviews</code> - 작성 후기 조회</summary>

로그인 사용자가 직접 작성한 후기만 최신순으로 조회합니다. 받은 후기는 조회할 수 없으며, 탈퇴한 상대방 정보는 `알 수 없음`으로 표시될 수 있습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "nickname": "한끼친구",
    "content": [
      {
        "reviewId": 1,
        "matchId": 1,
        "writerId": 1,
        "writerNickname": "한끼친구",
        "goodTags": [
          "ON_TIME"
        ],
        "badTags": [],
        "tagScoreDelta": 1,
        "doNotWantToMeetAgainSelected": false,
        "createdAt": "2026-06-22T12:30:00"
      }
    ]
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/reports</code> - 게시글 신고 접수</summary>

로그인 사용자가 다른 사용자의 게시글을 신고합니다. 본인 게시글은 신고할 수 없으며, 동일 게시글에 대한 `PENDING` 또는 `ACCEPTED` 신고가 있으면 중복 신고할 수 없습니다. 기각된 신고는 게시글이 수정되지 않은 경우 3일 이내 재신고가 제한됩니다.

**Request Body**

```json
{
  "targetId": 1,
  "reason": "SPAM",
  "detail": "string"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `targetId` | 필수 | 신고 대상 게시글 ID |
| `reason` | 필수 | `SPAM`, `OBSCENE`, `FRAUD`, `ABUSE`, `OTHER` 중 하나 |
| `detail` | 선택 | 신고 상세 내용, 최대 500자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reportId": 1,
    "targetId": 1,
    "status": "PENDING",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/disputes</code> - 이의제기 생성</summary>

노쇼 예정 상태(`HOST_NO_SHOW`, `GUEST_NO_SHOW`, `BOTH_NO_SHOW`)의 매칭 당사자가 노쇼 판정 시각부터 24시간 안에 이의제기를 제출합니다. 같은 매칭에는 한 번만 제출할 수 있으며, 제출 후 만남 인증 상태는 `DISPUTE`로 변경됩니다.

**Request Body**

```json
{
  "disputeType": "FUNERAL_CEREMONY",
  "reason": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `disputeType` | string | Y | `FUNERAL_CEREMONY`, `MEDICAL_EMERGENCY`, `PHONE_MALFUNCTION`, `GPS_ERROR`, `QR_ERROR` 중 하나 |
| `reason` | string | Y | 이의제기 상세 사유. 최대 1,000자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "disputeType": "FUNERAL_CEREMONY",
    "status": "SUBMITTED",
    "submittedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/disputes/me</code> - 내 이의제기 상세 조회</summary>

해당 매칭에 본인이 제출한 이의제기 상세를 조회합니다. 상태는 `SUBMITTED`, `UNDER_REVIEW`, `ACCEPTED`, `PARTIALLY_ACCEPTED`, `REJECTED`, `HOLD` 중 하나이며, 판정 전에는 `adminComment`와 `processedAt`이 `null`입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "disputeType": "FUNERAL_CEREMONY",
    "reason": "상세 사유입니다",
    "status": "SUBMITTED",
    "adminComment": null,
    "submittedAt": "2026-06-22T12:30:00",
    "processedAt": null,
    "holdDeadlineAt": null
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/disputes/me</code> - 내 이의제기 목록 조회</summary>

로그인 사용자가 제출한 모든 이의제기를 최신 제출순으로 조회합니다. 이의제기가 없으면 빈 배열을 반환합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "disputeId": 1,
      "matchId": 1,
      "disputeType": "FUNERAL_CEREMONY",
      "status": "SUBMITTED",
      "submittedAt": "2026-06-22T12:30:00"
    }
  ]
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/disputes/resubmit</code> - 이의제기 재제출</summary>

관리자가 `HOLD`로 보류한 이의제기에만 추가 사유를 제출할 수 있습니다. 보류 판정 후 24시간 이내에 한 번만 재제출할 수 있으며, 최초 제출과 동일한 `disputeType`을 사용해야 합니다.

**Request Body**

```json
{
  "disputeType": "FUNERAL_CEREMONY",
  "reason": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `disputeType` | string | Y | 최초 이의제기와 동일한 유형 |
| `reason` | string | Y | 보완 사유. 최대 1,000자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "disputeType": "FUNERAL_CEREMONY",
    "status": "SUBMITTED",
    "submittedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/inquiries</code> - 문의 생성</summary>

로그인 사용자가 고객 문의를 접수합니다. 하루 최대 20회까지 가능하며, 직전 문의 접수 후 1분이 지나야 다시 접수할 수 있습니다. 정지 계정은 `ACCOUNT` 유형만 접수할 수 있습니다.

**Request Body**

```json
{
  "title": "문의 제목입니다",
  "content": "요청 내용입니다",
  "type": "ACCOUNT"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | string | Y | 문의 제목. 최대 200자 |
| `content` | string | Y | 문의 내용 |
| `type` | string | Y | `ACCOUNT`, `PAYMENT`, `USAGE`, `HISTORY`, `MATCH`, `REPORT`, `OTHER` 중 하나 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "inquiryId": 1,
    "status": "PENDING",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/inquiries/{inquiryId}</code> - 문의 상세 조회</summary>

본인이 접수한 문의의 내용과 관리자 답변을 조회합니다. 답변 전에는 `answer`가 `null`이며, `answerStatus`는 `PENDING`, `READ`, `ANSWERED`, `WITHDRAWN` 중 하나입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "inquiryId": 1,
    "title": "문의 제목입니다",
    "content": "요청 내용입니다",
    "type": "ACCOUNT",
    "answerStatus": "PENDING",
    "answer": null,
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/inquiries/me</code> - 내 문의 목록 조회</summary>

로그인 사용자가 접수한 문의를 최신순으로 조회합니다. 취소된 `WITHDRAWN` 문의도 목록에 포함됩니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "inquiryId": 1,
        "title": "문의 제목입니다",
        "type": "ACCOUNT",
        "answerStatus": "PENDING",
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/inquiries/{inquiryId}/cancel</code> - 문의 취소</summary>

본인이 접수한 문의를 취소합니다. `PENDING` 또는 `READ` 상태에서만 취소할 수 있으며, 답변 완료(`ANSWERED`) 또는 이미 취소된 문의는 취소할 수 없습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "inquiryId": 1,
    "cancelledAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/payments</code> - 결제 준비</summary>

결제 금액과 포인트를 확정하고 PortOne 결제에 사용할 `merchantUid`를 발급합니다. 이 시점의 결제 상태는 `READY`입니다.

**Request Body**

```json
{
  "chargePoint": 3000,
  "payMethod": "CARD"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `chargePoint` | number | Y | 충전 포인트. `3000`, `5000`, `10000`, `20000` 중 하나 |
| `payMethod` | string | Y | 결제 수단 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": 1,
    "merchantUid": "merchant_20260622123000",
    "chargePoint": 3000,
    "amount": 3000,
    "status": "READY",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/payments/{paymentId}/verify</code> - 결제 검증</summary>

PortOne 결제 식별자(`impUid`)로 실제 결제 금액을 검증합니다. 검증에 성공하면 결제 상태가 `PAID`로 변경되고 포인트가 충전됩니다.

**Request Body**

```json
{
  "impUid": "imp_1234567890"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `impUid` | string | Y | PortOne이 발급한 결제 식별자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": 1,
    "impUid": "imp_1234567890",
    "chargePoint": 3000,
    "amount": 3000,
    "status": "PAID",
    "balanceAfter": 13000,
    "completedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/payments/me</code> - 결제 내역 조회</summary>

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기. 최대 `50` |

결제 상태는 `READY`, `PAID`, `CANCELLED`, `FAILED` 중 하나이며, `completedAt`은 `PAID` 상태일 때만 설정됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "paymentId": 1,
        "chargePoint": 3000,
        "amount": 3000,
        "payMethod": "CARD",
        "status": "PAID",
        "createdAt": "2026-06-22T12:30:00",
        "completedAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/payments/{paymentId}/cancel</code> - 결제 취소</summary>

완료된(`PAID`) 본인 결제를 PortOne에 환불 요청합니다. 이미 충전 포인트를 사용한 결제는 취소할 수 없으며, 성공하면 상태가 `CANCELLED`로 변경됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": 1,
    "status": "CANCELLED",
    "refundedAmount": 3000,
    "cancelledAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/payments/{paymentId}/fail</code> - 결제 실패 처리</summary>

클라이언트 결제창에서 취소 또는 실패했을 때 호출합니다. `READY` 결제를 `FAILED`로 변경하며, 이미 완료·취소·실패된 결제는 상태를 변경하지 않고 정상 응답합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null
}
```

</details>

<details>
<summary><code>GET /api/v1/me/points/transactions</code> - 포인트 거래 내역 조회</summary>

로그인 사용자의 포인트 변동 이력을 최신순으로 조회합니다. `type`을 생략하면 전체 내역을 반환하며, `amount`는 적립·반환이면 양수, 예치·차감이면 음수입니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `type` | N | - | 거래 유형 필터 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기. `1`~`50` |

`type`에는 `JOIN_BONUS`, `CHARGE`, `CHARGE_CANCELLED`, `DEPOSIT`, `EDIT_DEPOSIT`, `REFUND`, `PARTIAL_REFUND`, `PENALTY`, `REPORT_REWARD`, `REVIEW_REWARD`를 사용할 수 있습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "transactionId": 1,
        "userId": 1,
        "matchId": 1,
        "referenceType": "MATCH",
        "referenceId": 1,
        "settlementReason": "APPLICANT_DEPOSIT",
        "amount": -1000,
        "transactionType": "DEPOSIT",
        "balanceAfter": 9000,
        "description": null,
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/notifications</code> - 알림 목록 조회</summary>

로그인 사용자의 알림을 최신 ID 순으로 커서 기반 조회합니다. `cursorId` 없이 요청하면 첫 페이지를 조회하고, 다음 페이지는 이전 응답의 `nextCursor`를 전달합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `cursorId` | N | - | 이전 응답의 `nextCursor`. 첫 요청에서는 생략 |
| `size` | N | `20` | 조회 개수. 최대 `50` |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "notificationId": 1,
        "type": "MATCH_APPLIED",
        "title": "새로운 신청자가 있습니다.",
        "content": "게시글에 새로운 신청자가 있습니다. 신청 내용을 확인해 주세요.",
        "domain": "MATCH",
        "relatedId": 1,
        "isRead": false,
        "readAt": null,
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "hasNext": false,
    "nextCursor": null
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/notifications/read-all</code> - 전체 알림 읽음</summary>

로그인 사용자의 미확인 알림 전체를 읽음 처리합니다. 이미 읽은 알림은 변경하지 않으며, `updatedCount`는 실제로 변경된 알림 수입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "updatedCount": 1
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/notifications/{notificationId}/read</code> - 단건 알림 읽음</summary>

본인 알림 한 건을 읽음 처리합니다. 이미 읽은 알림을 다시 요청해도 현재 읽음 상태를 반환하며, 다른 사용자의 알림은 처리할 수 없습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "notificationId": 1,
    "isRead": true,
    "readAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/notifications/subscribe</code> - 알림 SSE 구독</summary>

실시간 알림을 받기 위한 SSE 연결입니다. `Accept: text/event-stream`으로 연결하며, 연결 직후 `connect` 이벤트가 전송됩니다. 연결은 최대 30분 유지됩니다.

**Request Body**

요청 바디 없음

**Response — `text/event-stream`**

```text
event: connect
data: SSE 연결 완료

event: notification
data: {"notificationId":1,"type":"MATCH_APPLIED","title":"새로운 신청자가 있습니다.","content":"게시글에 새로운 신청자가 있습니다. 신청 내용을 확인해 주세요.","domain":"MATCH","relatedId":1,"isRead":false,"readAt":null,"createdAt":"2026-06-22T12:30:00"}
```

</details>

<details>
<summary><code>GET /api/v1/notifications/unread-count</code> - 미확인 알림 수 조회</summary>

로그인 사용자의 읽지 않은 알림 개수를 조회합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "unreadCount": 1
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/chat-rooms/{chatRoomId}/messages</code> - 채팅 메시지 조회</summary>

채팅방 참여자만 메시지를 최신순으로 커서 기반 조회할 수 있습니다. `READ_ONLY` 채팅방은 조회할 수 있지만, 매칭 취소로 `DEACTIVATED` 된 채팅방은 조회할 수 없습니다. 조회한 상대방 메시지는 읽음 처리됩니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `cursorId` | N | `9999999999` | 이보다 작은 메시지 ID를 조회하는 커서 |
| `size` | N | `20` | 조회 개수. 최대 `50` |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "messageId": 1,
        "chatRoomId": 1,
        "senderId": 1,
        "senderNickname": "한끼친구",
        "content": "요청 내용입니다",
        "systemMessage": false,
        "isRead": false,
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "hasNext": false,
    "nextCursor": null
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/chat-rooms/{chatRoomId}/members</code> - 채팅방 멤버 조회</summary>

채팅방 참여자만 현재 참여 중인 멤버를 조회할 수 있습니다. 매칭 취소로 퇴장 처리된 `LEFT` 멤버는 반환하지 않으며, `DEACTIVATED` 채팅방은 조회할 수 없습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": [
    {
      "userId": 1,
      "nickname": "한끼친구",
      "joinedAt": "2026-06-22T12:30:00"
    }
  ]
}
```

</details>

<details>
<summary><code>POST /api/v1/ai/matching/chat/stream</code> - AI 식사팟 매칭 추천</summary>

로그인 사용자의 자연어 식사 조건을 바탕으로 같은 학교의 모집 중인 식사팟을 추천합니다. 메뉴·시간·분위기·인원 조건을 반영하며, 정확 후보가 없으면 가까운 만남 시간의 후보를 대체 추천할 수 있습니다.

**Request Body**

```json
{
  "conversationId": null,
  "message": "오늘 저녁 조용하게 밥 먹을 사람 찾아줘"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `conversationId` | 선택 | 대화 세션 ID. 첫 요청은 `null` 또는 생략 가능 |
| `message` | 필수 | 자연어 식사 조건 |

**Response Body — `text/event-stream`**

```text
data: 요청 조건에 맞는 식사팟을 찾아볼게요.

data: 오늘 18:30에 만나는 후보가 있습니다.
```

</details>

<details>
<summary><code>DELETE /api/v1/ai/matching/chat/{conversationId}</code> - AI 매칭 대화 세션 삭제</summary>

현재 로그인 사용자의 AI 매칭 대화 이력과 직전 추천 상태를 정리합니다.

**Request Body**

요청 바디 없음

**Response**

`204 No Content`

</details>

<details>
<summary><code>POST /api/v1/ai/support/chat/stream</code> - AI 고객센터 상담</summary>

계정, 매칭, 포인트, 노쇼, 채팅, 신고, 후기 정책을 안내합니다. 개인 상태가 필요한 경우 로그인 사용자의 기본 정보와 보유 포인트를 조회해 안내합니다.

**Request Body**

```json
{
  "conversationId": null,
  "message": "매칭 취소하면 포인트는 어떻게 되나요?"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `conversationId` | 선택 | 대화 세션 ID. 첫 요청은 `null` 또는 생략 가능 |
| `message` | 필수 | 고객센터 문의 내용 |

**Response Body — `text/event-stream`**

```text
data: 매칭 취소 시 포인트 반환 정책을 안내해드릴게요.
```

</details>

<details>
<summary><code>POST /api/v1/admin/auth/login</code> - 관리자 로그인</summary>

관리자 이메일과 비밀번호로 로그인해 관리자 API에 사용하는 `adminAccessToken`을 발급받습니다.

**Request Body**

```json
{
  "email": "student@university.ac.kr",
  "password": "Password123!"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | string | Y | 관리자 이메일 |
| `password` | string | Y | 관리자 비밀번호 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "adminId": 1,
    "name": "홍길동",
    "role": "SUPER_ADMIN",
    "adminAccessToken": "sample-token"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/users</code> - 회원 목록 조회</summary>

관리자가 회원 목록을 가입일 최신순으로 조회합니다. 상태와 키워드로 필터링할 수 있습니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `status` | N | - | `ACTIVE`, `SUSPENDED`, `WITHDRAWN` 중 하나 |
| `keyword` | N | - | 회원 검색어 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "userId": 1,
        "email": "student@university.ac.kr",
        "name": "홍길동",
        "nickname": "한끼친구",
        "universityName": "홍길동",
        "point": 1000,
        "mannerTemperature": 36.5,
        "status": "ACTIVE",
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/users/{userId}/suspend</code> - 회원 정지</summary>

활성 `SUPER_ADMIN`이 사용자를 영구 정지합니다. 정지된 사용자에게 계정 정지 알림이 발송됩니다.

**Request Body**

```json
{
  "reason": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | Y | 정지 사유. 최대 500자, 한글·영문·숫자·공백만 사용 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "status": "SUSPENDED",
    "reason": "상세 사유입니다",
    "suspendedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/users/{userId}/reinstate</code> - 회원 정지 해제</summary>

활성 `SUPER_ADMIN`이 `SUSPENDED` 사용자를 `ACTIVE`로 복구합니다. 정지 상태가 아닌 사용자는 해제할 수 없으며, 사용자에게 해제 알림이 발송됩니다.

**Request Body**

```json
{
  "reason": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reason` | string | Y | 정지 해제 사유. 최대 500자, 한글·영문·숫자·공백만 사용 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "status": "ACTIVE",
    "reason": "상세 사유입니다",
    "reinstatedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>DELETE /api/v1/admin/posts/{postId}</code> - 게시글 강제 삭제</summary>

활성 `SUPER_ADMIN`이 `OPEN` 게시글을 강제 삭제하고 등록자의 예치 포인트를 환불합니다. `reportId`를 전달하면 해당 신고가 대상 게시글과 일치해야 하며, `PENDING` 신고는 채택 처리 후 삭제됩니다. `reportId` 없이 직권 삭제할 때는 대기 중인 신고가 없어야 합니다.

**Request Body**

```json
{
  "reportId": 1,
  "reason": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `reportId` | number | N | 삭제 근거가 되는 신고 ID |
| `reason` | string | Y | 강제 삭제 사유 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "reportId": 1,
    "reason": "상세 사유입니다",
    "refundedPoint": 1000,
    "deletedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/posts</code> - 게시글 목록 조회</summary>

관리자가 게시글을 대학·작성자·상태·삭제 여부·키워드로 필터링해 조회합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `universityId` | N | - | 작성자 대학 ID |
| `authorNickname` | N | - | 작성자 닉네임 |
| `status` | N | - | `OPEN`, `MATCHED`, `COMPLETED`, `CANCELLED`, `EXPIRED` 중 하나 |
| `deleted` | N | - | 강제 삭제 여부 |
| `keyword` | N | - | 게시글 내용 검색어 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "postId": 1,
        "authorNickname": "한끼친구",
        "placeName": "홍길동",
        "content": "요청 내용입니다",
        "meetAt": "2026-06-22T12:30:00",
        "authorDeposit": 1000,
        "status": "OPEN",
        "createdAt": "2026-06-22T12:30:00",
        "deleted": false,
        "deletedAt": null
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/posts/{postId}</code> - 게시글 상세 조회</summary>

관리자가 삭제된 게시글을 포함해 게시글 상세를 조회합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "status": "OPEN",
    "authorDeposit": 1000,
    "content": "요청 내용입니다",
    "placeName": "홍길동",
    "meetAt": "2026-06-22T12:30:00",
    "authorNickname": "한끼친구",
    "createdAt": "2026-06-22T12:30:00",
    "deleted": false,
    "deletedAt": null
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/admin/posts/{postId}/restore</code> - 게시글 복구</summary>

활성 `SUPER_ADMIN`이 강제 삭제된 게시글을 복구합니다. 복구 과정에서 책임비가 다시 예치되며, 삭제되지 않은 게시글은 복구할 수 없습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "postId": 1,
    "redepositedPoint": 1000,
    "restoredAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/reports</code> - 신고 목록 조회</summary>

관리자가 신고 처리 상태별 목록을 조회합니다.

**Query Parameters**

| Parameter | Required | Default | Description |
| --- | --- | --- | --- |
| `status` | 선택 | - | `PENDING`, `ACCEPTED`, `REJECTED`, `WITHDRAWN` |
| `page` | 선택 | `0` | 페이지 번호 |
| `size` | 선택 | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "reportId": 1,
        "reporterNickname": "한끼친구",
        "targetId": 1,
        "reason": "SPAM",
        "detail": "string",
        "status": "PENDING",
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/reports/{reportId}</code> - 신고 상세 조회</summary>

처리 전 신고의 `processedAt`은 `null`입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reporterNickname": "한끼친구",
    "targetId": 1,
    "reason": "SPAM",
    "detail": "string",
    "status": "PENDING",
    "processedAt": null,
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/reports/{reportId}/process</code> - 신고 처리</summary>

관리자가 대기 중인 신고를 채택 또는 기각합니다. `reportStatus`에는 `ACCEPTED` 또는 `REJECTED`만 사용할 수 있으며, 이미 처리된 신고는 다시 처리할 수 없습니다. 채택된 신고는 월 포상 한도 내에서 신고자에게 50P를 지급합니다.

**Request Body**

```json
{
  "reportStatus": "ACCEPTED",
  "comment": "상세 사유입니다"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `reportStatus` | 필수 | `ACCEPTED` 또는 `REJECTED` |
| `comment` | 선택 | 처리 사유, 최대 1,000자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reportId": 1,
    "status": "ACCEPTED",
    "isRewarded": true,
    "rewardPoint": 50,
    "processedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/inquiries/{inquiryId}</code> - 문의 상세 조회</summary>

관리자가 고객 문의 상세와 답변을 조회합니다. `PENDING` 문의를 처음 상세 조회하면 상태가 `READ`로 변경됩니다. 답변이 없는 경우 `answer`는 `null`입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "inquiryId": 1,
    "userNickname": "한끼친구",
    "userEmail": "student@university.ac.kr",
    "universityName": "홍길동",
    "title": "문의 제목입니다",
    "content": "요청 내용입니다",
    "type": "ACCOUNT",
    "answerStatus": "READ",
    "answer": null,
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/inquiries</code> - 문의 목록 조회</summary>

관리자가 고객 문의 목록을 상태·유형별로 조회합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `status` | N | - | `PENDING`, `READ`, `ANSWERED`, `WITHDRAWN` 중 하나 |
| `type` | N | - | `ACCOUNT`, `PAYMENT`, `USAGE`, `HISTORY`, `MATCH`, `REPORT`, `OTHER` 중 하나 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "inquiryId": 1,
        "userNickname": "한끼친구",
        "title": "문의 제목입니다",
        "type": "ACCOUNT",
        "answerStatus": "PENDING",
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/admin/inquiries/{inquiryId}/answers</code> - 문의 답변 작성</summary>

관리자가 문의에 답변을 등록합니다. 답변 등록 후 문의 상태는 `ANSWERED`가 되고, 문의 작성자에게 알림이 발송됩니다. 이미 답변이 등록된 문의에는 다시 답변을 작성할 수 없습니다.

**Request Body**

```json
{
  "content": "요청 내용입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | string | Y | 답변 내용. 최대 2,000자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "answerId": 1,
    "inquiryId": 1,
    "adminName": "홍길동",
    "content": "요청 내용입니다",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/disputes/{disputeId}</code> - 이의제기 상세 조회</summary>

관리자가 이의제기 상세, GPS 인증 시각, 관련 채팅 내역을 조회합니다. 상세 조회 시 `SUBMITTED` 상태는 자동으로 `UNDER_REVIEW`로 변경됩니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "applicantNickname": "한끼친구",
    "disputeType": "FUNERAL_CEREMONY",
    "reason": "상세 사유입니다",
    "status": "UNDER_REVIEW",
    "verificationStatus": "DISPUTE",
    "authorPlaceVerifiedAt": null,
    "applicantPlaceVerifiedAt": null,
    "submittedAt": "2026-06-22T12:30:00",
    "chatMessages": []
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/disputes</code> - 이의제기 목록 조회</summary>

관리자가 이의제기 목록을 상태별로 조회합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `status` | N | - | `SUBMITTED`, `UNDER_REVIEW`, `ACCEPTED`, `PARTIALLY_ACCEPTED`, `REJECTED`, `HOLD` 중 하나 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "disputeId": 1,
        "matchId": 1,
        "applicantNickname": "한끼친구",
        "reason": "상세 사유입니다",
        "status": "SUBMITTED",
        "submittedAt": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/disputes/{disputeId}/judge</code> - 이의제기 판정</summary>

관리자가 `UNDER_REVIEW` 또는 `HOLD` 상태의 이의제기를 판정합니다. `ACCEPTED`는 노쇼 취소·포인트 정산, `PARTIALLY_ACCEPTED`는 일부 수용 정산, `REJECTED`는 노쇼 확정, `HOLD`는 추가 자료 제출 대기 처리입니다.

**Request Body**

```json
{
  "status": "ACCEPTED",
  "comment": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | string | Y | `ACCEPTED`, `PARTIALLY_ACCEPTED`, `REJECTED`, `HOLD` 중 하나 |
| `comment` | string | Y | 판정 사유. 최대 1,000자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "status": "ACCEPTED",
    "adminComment": "상세 사유입니다",
    "refundedPoint": 1000,
    "processedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/disputes/{disputeId}/override</code> - 이의제기 상태 변경</summary>

관리자가 오판정을 정정하기 위해 상태를 강제로 변경합니다. 일반 판정 흐름과 달리 상태 전이 제약 없이 변경되며, 포인트를 추가 정산하지 않습니다.

**Request Body**

```json
{
  "status": "UNDER_REVIEW",
  "comment": "상세 사유입니다"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | string | Y | 변경할 이의제기 상태 |
| `comment` | string | Y | 강제 변경 사유. 최대 1,000자 |

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "status": "UNDER_REVIEW",
    "adminComment": "상세 사유입니다",
    "refundedPoint": 1000,
    "processedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/payments</code> - 결제 목록 조회</summary>

관리자가 전체 결제 내역을 조회합니다. 특정 `userId` 또는 결제 상태로 필터링할 수 있습니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `userId` | N | - | 특정 사용자 결제 내역 필터 |
| `status` | N | - | `READY`, `PAID`, `CANCELLED`, `FAILED` 중 하나 |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기. 최대 `50` |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "paymentId": 1,
        "userId": 1,
        "merchantUid": "merchant_20260622123000",
        "chargePackage": "P_3000",
        "chargePoint": 3000,
        "amount": 3000,
        "payMethod": "CARD",
        "status": "PAID",
        "cancelReason": null,
        "failReason": null,
        "createdAt": "2026-06-22T12:30:00",
        "completedAt": "2026-06-22T12:30:00",
        "cancelledAt": null
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/no-show-candidates</code> - 노쇼 후보 조회</summary>

관리자가 노쇼 상태의 만남 인증 건을 최신순으로 조회합니다. 이의제기 제출 여부와, 제출 가능한 마감 시각(`disputeDeadline`)을 함께 확인할 수 있습니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `page` | N | `0` | 0부터 시작하는 페이지 번호 |
| `size` | N | `20` | 페이지 크기 |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "matchId": 1,
        "verificationStatus": "GUEST_NO_SHOW",
        "hostNickname": "한끼친구",
        "guestNickname": "한끼친구",
        "meetAt": "2026-06-22T12:30:00",
        "hasDispute": false,
        "disputeDeadline": "2026-06-22T12:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/notifications</code> - 관리자 알림 목록</summary>

로그인한 관리자의 알림을 최신 ID 순으로 커서 기반 조회합니다. 일반 사용자 알림과 관리자의 알림은 분리되어 있으며, 다음 페이지는 이전 응답의 `nextCursor`를 전달해 조회합니다.

**Query Parameters**

| 파라미터 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `cursorId` | N | - | 이전 응답의 `nextCursor`. 첫 요청에서는 생략 |
| `size` | N | `20` | 조회 개수. 최대 `50` |

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "content": [
      {
        "notificationId": 1,
        "type": "MATCH_APPLIED",
        "title": "새로운 신고가 접수되었습니다.",
        "content": "새로운 신고가 접수되었습니다. 검토해 주세요.",
        "domain": "REPORT",
        "relatedId": 1,
        "isRead": false,
        "readAt": null,
        "createdAt": "2026-06-22T12:30:00"
      }
    ],
    "hasNext": false,
    "nextCursor": null
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/notifications/read-all</code> - 관리자 알림 전체 읽음</summary>

로그인한 관리자의 미확인 알림 전체를 읽음 처리합니다. `updatedCount`는 실제 변경된 알림 수입니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "updatedCount": 1
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/notifications/{notificationId}/read</code> - 관리자 알림 단건 읽음</summary>

본인 관리자 알림 한 건을 읽음 처리합니다. 이미 읽은 알림은 현재 상태를 반환하며, 다른 관리자의 알림은 처리할 수 없습니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "notificationId": 1,
    "isRead": true,
    "readAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/notifications/unread-count</code> - 관리자 미확인 알림 수</summary>

로그인한 관리자의 읽지 않은 알림 개수를 조회합니다.

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "unreadCount": 1
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/notifications/subscribe</code> - 관리자 알림 SSE</summary>

관리자 실시간 알림 SSE 연결입니다. `Accept: text/event-stream`으로 연결하면 먼저 `connect` 이벤트가 전송되고, 이후 알림이 발생할 때마다 `notification` 이벤트가 전송됩니다. 연결은 최대 30분 유지됩니다.

**Request Body**

요청 바디 없음

**Response — `text/event-stream`**

```text
event: connect
data: SSE 연결 완료

event: notification
data: {"notificationId":1,"type":"REPORT_SUBMITTED","title":"새로운 신고가 접수되었습니다.","content":"새로운 신고가 접수되었습니다. 검토해 주세요.","domain":"REPORT","relatedId":1,"isRead":false,"readAt":null,"createdAt":"2026-06-22T12:30:00"}
```

</details>

<details>
<summary><code>POST /api/v1/admin/ai/reports/chat/stream</code> - 관리자 신고·이의제기 검토 AI 상담</summary>

관리자가 신고 또는 이의제기 상세를 검토할 때 판단 근거와 운영 정책 안내를 받습니다. AI는 검토를 보조하며, 신고 처리나 이의제기 최종 판정을 직접 실행하지 않습니다.

**Request Body**

```json
{
  "conversationId": null,
  "message": "신고 12번의 처리 판단 근거를 요약해줘"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `conversationId` | 선택 | 관리자 AI 대화 세션 ID. 첫 요청은 `null` 또는 생략 가능 |
| `message` | 필수 | 신고·이의제기 검토 요청 |

**Response Body — `text/event-stream`**

```text
data: 신고 상세와 정책 기준을 바탕으로 검토 결과를 안내합니다.
```

</details>

<details>
<summary><code>POST /api/v1/admin/ai/console/chat/stream</code> - 관리자 운영 현황·정책 안내 AI 상담</summary>

관리자가 대시보드 운영 현황, 처리 대기 건수, 운영 정책 또는 관리자 화면 사용 방법을 질문할 때 사용합니다. 이 경로는 신고 검토 경로와 같은 스트리밍 처리 로직을 사용하지만 운영 콘솔 진입 맥락을 나타냅니다.

**Request Body**

```json
{
  "conversationId": null,
  "message": "오늘 관리자 화면에서 우선 확인할 운영 현황을 알려줘"
}
```

| Field | Required | Description |
| --- | --- | --- |
| `conversationId` | 선택 | 관리자 AI 대화 세션 ID. 첫 요청은 `null` 또는 생략 가능 |
| `message` | 필수 | 운영 현황 또는 정책 안내 요청 |

**Response Body — `text/event-stream`**

```text
data: 현재 운영 현황을 기준으로 우선 확인할 항목을 안내합니다.
```

</details>

## WebSocket / STOMP

| Type | Destination | Body | 설명 |
| --- | --- | --- | --- |
| Handshake | `/ws/chat?token=Bearer {accessToken}` | - | JWT 검증 후 연결. SockJS fallback 지원 |
| Client publish | `/pub/chat/rooms/{chatRoomId}` | `ChatMessageRequestDto` | 채팅 메시지 전송 |
| Server subscribe | `/user/sub/chat/rooms/{chatRoomId}` | `ChatMessageResponseDto` | 사용자별 채팅 메시지 수신 |
| Server subscribe | `/user/queue/errors` | `String` | 채팅 오류 수신 |

`/pub/chat/rooms/{chatRoomId}`로 전송하는 메시지 형식입니다. 발신자 ID는 Handshake JWT에서 식별하므로 클라이언트가 보내지 않습니다. 메시지는 욕설 필터링 후 저장·전송됩니다.

```json
{
  "content": "안녕하세요! 어디에서 만날까요?"
}
```

성공하면 채팅방의 활성 멤버가 각자의 `/user/sub/chat/rooms/{chatRoomId}`에서 다음 형식의 메시지를 수신합니다.

```json
{
  "messageId": 1,
  "chatRoomId": 1,
  "senderId": 1,
  "senderNickname": "한끼친구",
  "content": "안녕하세요! 어디에서 만날까요?",
  "systemMessage": false,
  "isRead": false,
  "createdAt": "2026-06-22T12:30:00"
}
```

채팅방이 `READ_ONLY`이거나 `DEACTIVATED` 상태, 또는 발신자가 참여자가 아닌 경우에는 메시지가 전송되지 않고 `/user/queue/errors`로 오류 문자열이 전달됩니다.

## Enum 값

| Enum | Values |
| --- | --- |
| AdminRole | `SUPER_ADMIN` |
| AiCallStatus | `SUCCESS`, `FAILED`, `FALLBACK` |
| AiChatMemoryRole | `USER`, `ASSISTANT` |
| AiErrorType | `TIMEOUT`, `RATE_LIMIT`, `SERVER_ERROR`, `INVALID_API_KEY`, `INVALID_RESPONSE`, `SCHEMA_VALIDATION_FAILED`, `TOOL_ERROR`, `TOOL_TIMEOUT`, `TOOL_NOT_FOUND`, `PROMPT_LOAD_ERROR`, `PROMPT_NOT_FOUND`, `CONTENT_FILTERED`, `FALLBACK_FAILED`, `UNKNOWN` |
| AiFeature | `MATCHING`, `SUPPORT`, `REPORT` |
| AiPromptType | `MATCHING_CHAT`, `SUPPORT_CHAT`, `REPORT_SUMMARY` |
| AiAdminAnswerSource | `TOOL`, `RAG`, `TOOL_AND_RAG`, `GPT_GENERAL`, `FALLBACK` |
| AiAdminCategory | `DASHBOARD`, `POST`, `REPORT`, `INQUIRY`, `DISPUTE`, `USER`, `PAYMENT`, `FAQ`, `GENERAL` |
| AiReportChatAction | `ANALYZE_REPORT`, `ANALYZE_DISPUTE`, `HIGH_RISK_USERS`, `DASHBOARD_SUMMARY`, `GENERAL_GUIDE`, `CLARIFY` |
| AiReportDecisionSuggestion | `ACCEPT`, `REJECT`, `NEEDS_REVIEW` |
| AiReportRiskLevel | `LOW`, `MEDIUM`, `HIGH` |
| AiSupportCategory | `MATCH`, `POST`, `POINT`, `CHAT`, `REPORT`, `ACCOUNT`, `MEET`, `REVIEW`, `GENERAL` |
| AiSupportMessageRole | `USER`, `ASSISTANT` |
| ChatMemberRole | `HOST`, `GUEST` |
| ChatMemberStatus | `ACTIVE`, `NO_SHOW`, `LEFT` |
| ChatRoomStatus | `ACTIVE`, `READ_ONLY`, `DEACTIVATED` |
| ChatRoomType | `ONE_TO_ONE`, `GROUP` |
| DisputeStatus | `SUBMITTED`, `UNDER_REVIEW`, `ACCEPTED`, `PARTIALLY_ACCEPTED`, `REJECTED`, `HOLD` |
| DisputeType | `FUNERAL_CEREMONY`, `MEDICAL_EMERGENCY`, `PHONE_MALFUNCTION`, `GPS_ERROR`, `QR_ERROR`, `ADMIN_OVERRIDE` |
| InquiryAnswerStatus | `PENDING`, `READ`, `ANSWERED`, `WITHDRAWN` |
| InquiryType | `ACCOUNT`, `PAYMENT`, `USAGE`, `HISTORY`, `MATCH`, `REPORT`, `OTHER` |
| LocationRole | `AUTHOR`, `APPLICANT` |
| MatchStatus | `MATCHED`, `COMPLETED`, `CANCELLED`, `HOST_NO_SHOW`, `GUEST_NO_SHOW`, `BOTH_NO_SHOW`, `DISPUTED` |
| ExtensionStatus | `NONE`, `REQUESTED`, `ACCEPTED`, `REJECTED`, `EXPIRED` |
| VerificationStatus | `PENDING`, `VERIFIED`, `DONE`, `HOST_NO_SHOW`, `GUEST_NO_SHOW`, `BOTH_NO_SHOW`, `NO_SHOW_CANCELLED`, `NO_SHOW_CONFIRMED`, `DISPUTE` |
| NotificationReceiverType | `USER`, `ADMIN` |
| NotificationType | `MATCH_APPLIED`, `MATCH_CONFIRMED`, `MATCH_CANCELLED`, `MEET_REMINDER`, `MEET_IMMINENT`, `MEET_OVERDUE`, `MEET_COMPLETED`, `MEET_COMPLETED_AUTHOR`, `REVIEW_DEADLINE_REMINDER`, `REVIEW_REWARD`, `MANNER_TEMPERATURE_CHANGED`, `CHAT_RECEIVED`, `PLACE_VERIFIED`, `CHAT_MEMBER_LEFT`, `NO_SHOW_WARNING`, `OPPONENT_NO_SHOW_WARNING`, `NO_SHOW_CONFIRMED`, `MEET_EXTEND_REQUESTED`, `MEET_EXTEND_ACCEPTED`, `MEET_EXTEND_REJECTED`, `MEET_EXTEND_EXPIRED`, `DISPUTE_SUBMITTED`, `DISPUTE_RESULT`, `DISPUTE_PENDING`, `DISPUTE_DEADLINE_REMINDER`, `REPORT_SUBMITTED`, `REPORT_REWARD`, `REPORT_REJECTED`, `REPORT_RESULT`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`, `PAYMENT_CANCEL_SUCCESS`, `PAYMENT_CANCEL_FAILED`, `POINT_CHANGED`, `INQUIRY_SUBMITTED`, `INQUIRY_ANSWERED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_UNSUSPENDED`, `POST_WARNED_1`, `POST_WARNED_2`, `POST_EXPIRING_SOON`, `POST_EXPIRED`, `POST_DELETED`, `POST_RESTORED`, `SYSTEM` |
| RelatedDomain | `MATCH`, `MEET`, `CHAT`, `POINT`, `REPORT`, `DISPUTE`, `INQUIRY`, `ACCOUNT`, `POST`, `SYSTEM` |
| ChargePackage | `P_3000`, `3000`, `P_5000`, `5000`, `P_10000`, `10000`, `P_20000`, `20000` |
| PaymentStatus | `READY`, `PAID`, `CANCELLED`, `FAILED` |
| PointReferenceType | `MATCH`, `POST`, `PAYMENT` |
| PointSettlementReason | `APPLICANT_DEPOSIT`, `AUTHOR_DEPOSIT` |
| PointSource | `FREE`, `PAID` |
| PointTransactionType | `JOIN_BONUS`, `CHARGE`, `CHARGE_CANCELLED`, `DEPOSIT`, `EDIT_DEPOSIT`, `REFUND`, `PARTIAL_REFUND`, `PENALTY`, `REPORT_REWARD`, `REVIEW_REWARD` |
| PostStatus | `OPEN`, `MATCHED`, `COMPLETED`, `CANCELLED`, `EXPIRED` |
| ReportReason | `SPAM`, `OBSCENE`, `FRAUD`, `ABUSE`, `OTHER` |
| ReportStatus | `PENDING`, `ACCEPTED`, `REJECTED`, `WITHDRAWN` |
| ReviewBadTag | `LATE`, `NO_REPLY`, `UNCOMFORTABLE`, `BAD_MANNER`, `DO_NOT_WANT_TO_MEET_AGAIN` |
| ReviewGoodTag | `ON_TIME`, `1`, `KIND`, `1`, `GOOD_COMMUNICATION`, `1`, `CLEAN_MANNER`, `1`, `WANT_MEET_AGAIN`, `1` |
| MajorCategory | `COMPUTER_SCIENCE`, `ARTIFICIAL_INTELLIGENCE`, `SOFTWARE_ENGINEERING`, `ELECTRICAL_ELECTRONIC_ENGINEERING`, `WEDDING_BEAUTY`, `BUSINESS_ADMINISTRATION`, `ECONOMICS`, `ACCOUNTING`, `INTERNATIONAL_TRADE`, `PUBLIC_ADMINISTRATION`, `LAW`, `POLICE_ADMINISTRATION`, `SOCIAL_WELFARE`, `PSYCHOLOGY`, `CHILD_EDUCATION`, `KOREAN_LANGUAGE_LITERATURE`, `ENGLISH_LANGUAGE_LITERATURE`, `JAPANESE_LANGUAGE_LITERATURE`, `CHINESE_LANGUAGE_LITERATURE`, `MECHANICAL_ENGINEERING`, `CIVIL_ENGINEERING`, `ARCHITECTURE`, `CHEMICAL_ENGINEERING`, `INDUSTRIAL_ENGINEERING`, `DATA_SCIENCE`, `INFORMATION_SECURITY`, `GAME_ENGINEERING`, `MEDIA_CONTENTS`, `NURSING`, `PHYSICAL_THERAPY`, `CLINICAL_PATHOLOGY`, `DENTAL_HYGIENE`, `EMERGENCY_MEDICAL_SERVICE`, `DESIGN`, `VISUAL_DESIGN`, `INDUSTRIAL_DESIGN`, `FASHION_DESIGN`, `BEAUTY_ART`, `SPORTS_SCIENCE`, `FOOD_NUTRITION`, `HOTEL_TOURISM`, `CULINARY_ARTS`, `MUSIC`, `PRACTICAL_MUSIC`, `THEATER_FILM`, `FINE_ARTS` |
| Gender | `MALE`, `FEMALE` |
| UserStatus | `ACTIVE`, `SUSPENDED`, `WITHDRAWN` |
