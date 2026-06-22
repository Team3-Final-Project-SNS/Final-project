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

**Request Body**

```json
{
  "email": "student@university.ac.kr"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "expireSeconds": 1
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/email/otp/verify</code> - 이메일 OTP 검증</summary>

**Request Body**

```json
{
  "email": "student@university.ac.kr",
  "otpCode": "123456"
}
```

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

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userId": 1,
    "nickname": "한끼친구",
    "point": 1000,
    "accessToken": "sample-token"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/auth/login</code> - 로그인</summary>

**Request Body**

```json
{
  "email": "student@university.ac.kr",
  "password": "Password123!"
}
```

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

**Request Body**

```json
{
  "currentPassword": "Password123!",
  "newPassword": "Password123!",
  "nickname": "한끼친구",
  "major": "컴퓨터공학과"
}
```

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

**Request Body**

```json
{
  "password": "Password123!"
}
```

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

**Request Body**

```json
{
  "meetAt": "2026-06-22T12:30:00",
  "placeName": "홍길동",
  "placeLat": 37.5665,
  "placeLng": 126.978,
  "content": "요청 내용입니다",
  "authorDeposit": 1,
  "maxApplicants": 1
}
```

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
    "authorDeposit": 1,
    "status": "OPEN",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/posts</code> - 게시글 목록 조회</summary>

**Query Parameters**

`status=OPEN`, `page=0`, `size=20`, `sort=DEPOSIT_DESC`

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
        "authorDeposit": 1,
        "currentApplicants": 1,
        "maxApplicants": 1,
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
    "authorDeposit": 1,
    "currentApplicants": 1,
    "maxApplicants": 1,
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

**Request Body**

```json
{
  "meetAt": "2026-06-22T12:30:00",
  "placeName": "홍길동",
  "placeLat": 37.5665,
  "placeLng": 126.978,
  "content": "요청 내용입니다",
  "authorDeposit": 1
}
```

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
    "authorDeposit": 1,
    "status": "OPEN",
    "updatedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>DELETE /api/v1/posts/{postId}</code> - 게시글 삭제</summary>

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
    "authorDeposit": 1,
    "applicantDeposit": 1,
    "status": "MATCHED",
    "chatRoomId": 1,
    "matchedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}</code> - 매칭 상세 조회</summary>

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
    "authorDeposit": 1,
    "applicantDeposit": 1,
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
        "role": "string",
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

**Query Parameters**

`status`, `page=0`, `size=20`

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
        "myDeposit": 1,
        "isAuthor": true,
        "participants": [
          {
            "userId": 1,
            "matchId": 1,
            "nickname": "한끼친구",
            "major": "컴퓨터공학과",
            "studentNumber": "20241234",
            "role": "string",
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

**Request Body**

```json
{
  "reason": "상세 사유입니다"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "status": "MATCHED",
    "refundedPoint": 1000,
    "forfeitedPoint": 1000
  }
}
```

</details>

<details>
<summary><code>PUT /api/v1/matches/{matchId}/location</code> - 위치 업데이트</summary>

**Request Body**

```json
{
  "latitude": 37.5665,
  "longitude": 36.5
}
```

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
    "longitude": 36.5,
    "updatedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/location</code> - 위치 조회</summary>

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
      "longitude": 36.5,
      "updatedAt": "2026-06-22T12:30:00",
      "role": "AUTHOR"
    },
    "opponentLocation": {
      "latitude": 37.5665,
      "longitude": 36.5,
      "updatedAt": "2026-06-22T12:30:00",
      "role": "AUTHOR"
    },
    "opponentLocations": [
      {
        "latitude": 37.5665,
        "longitude": 36.5,
        "updatedAt": "2026-06-22T12:30:00",
        "role": "AUTHOR"
      }
    ]
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/place-verification</code> - GPS 장소 인증</summary>

**Request Body**

```json
{
  "currentLat": 37.5665,
  "currentLng": 126.978
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "verificationStatus": "PENDING",
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

**Request Body**

```json
{
  "qrToken": "sample-token"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "matchId": 1,
    "verificationStatus": "PENDING",
    "matchStatus": "MATCHED",
    "completedAt": "2026-06-22T12:30:00",
    "refundedPoint": 1000
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/verification</code> - 만남 인증 상태 조회</summary>

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
    "rejectedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/matches/{matchId}/extension</code> - 시간 연장 상태 조회</summary>

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

**Request Body**

```json
{
  "goodTags": [
    "ON_TIME"
  ],
  "badTags": [
    "LATE"
  ]
}
```

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
    "badTags": [
      "LATE"
    ],
    "tagScoreDelta": 1,
    "doNotWantToMeetAgainSelected": true,
    "rewardPoint": 1000,
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/me/reviews</code> - 작성 후기 조회</summary>

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
        "badTags": [
          "LATE"
        ],
        "tagScoreDelta": 1,
        "doNotWantToMeetAgainSelected": true,
        "createdAt": "2026-06-22T12:30:00"
      }
    ]
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/reports</code> - 신고 생성</summary>

**Request Body**

```json
{
  "targetId": 1,
  "reason": "SPAM",
  "detail": "string"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reportId": 1,
    "targetId": 1,
    "status": "string",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/matches/{matchId}/disputes</code> - 이의제기 생성</summary>

**Request Body**

```json
{
  "disputeType": "FUNERAL_CEREMONY",
  "reason": "상세 사유입니다"
}
```

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
    "adminComment": "상세 사유입니다",
    "submittedAt": "2026-06-22T12:30:00",
    "processedAt": "2026-06-22T12:30:00",
    "holdDeadlineAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/disputes/me</code> - 내 이의제기 목록 조회</summary>

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

**Request Body**

```json
{
  "disputeType": "FUNERAL_CEREMONY",
  "reason": "상세 사유입니다"
}
```

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

**Request Body**

```json
{
  "title": "문의 제목입니다",
  "content": "요청 내용입니다",
  "type": "ACCOUNT"
}
```

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
    "answer": {},
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/inquiries/me</code> - 내 문의 목록 조회</summary>

**Query Parameters**

`page=0`, `size=20`

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

**Request Body**

```json
{
  "chargePoint": 1000,
  "payMethod": "string"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": 1,
    "merchantUid": "merchant_20260622123000",
    "chargePoint": 1000,
    "amount": 1000,
    "status": "READY",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/payments/{paymentId}/verify</code> - 결제 검증</summary>

**Request Body**

```json
{
  "impUid": "imp_1234567890"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "paymentId": 1,
    "impUid": "imp_1234567890",
    "chargePoint": 1000,
    "amount": 1000,
    "status": "READY",
    "balanceAfter": 1,
    "completedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/payments/me</code> - 결제 내역 조회</summary>

**Query Parameters**

`page=0`, `size=20`

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
        "chargePoint": 1000,
        "amount": 1000,
        "payMethod": "string",
        "status": "READY",
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
    "status": "READY",
    "refundedAmount": 1000,
    "cancelledAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/payments/{paymentId}/fail</code> - 결제 실패 처리</summary>

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

**Query Parameters**

`type`, `page=0`, `size=20`

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
        "amount": 1000,
        "transactionType": "JOIN_BONUS",
        "balanceAfter": 1,
        "description": "string",
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

**Query Parameters**

`cursorId`, `size=20`

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
        "title": "문의 제목입니다",
        "content": "요청 내용입니다",
        "domain": "MATCH",
        "relatedId": 1,
        "isRead": true,
        "readAt": "2026-06-22T12:30:00",
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

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "event": "notification",
  "data": {
    "type": "MATCH_CREATED",
    "title": "새 알림",
    "content": "알림 내용입니다"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/notifications/unread-count</code> - 미확인 알림 수 조회</summary>

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

**Query Parameters**

`cursorId=9999999999`, `size=20`

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
        "systemMessage": true,
        "isRead": true,
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
<summary><code>POST /api/v1/ai/matching/chat/stream</code> - AI 매칭 SSE</summary>

**Request Body**

```json
{
  "conversationId": "string",
  "message": "string"
}
```

**Response Body**

```text
data: AI 응답 토큰

data: 다음 응답 토큰

```

</details>

<details>
<summary><code>DELETE /api/v1/ai/matching/chat/{conversationId}</code> - AI 매칭 대화 삭제</summary>

**Request Body**

요청 바디 없음

**Response Body**

응답 바디 없음

</details>

<details>
<summary><code>POST /api/v1/ai/support/chat/stream</code> - AI 고객센터 SSE</summary>

**Request Body**

```json
{
  "conversationId": "string",
  "message": "string"
}
```

**Response Body**

```text
data: AI 응답 토큰

data: 다음 응답 토큰

```

</details>

<details>
<summary><code>POST /api/v1/admin/auth/login</code> - 관리자 로그인</summary>

**Request Body**

```json
{
  "email": "student@university.ac.kr",
  "password": "Password123!"
}
```

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

**Query Parameters**

`status`, `keyword`, `page=0`, `size=20`

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

**Request Body**

```json
{
  "reason": "상세 사유입니다"
}
```

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
    "suspendedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/users/{userId}/reinstate</code> - 회원 정지 해제</summary>

**Request Body**

```json
{
  "reason": "상세 사유입니다"
}
```

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

**Request Body**

```json
{
  "reportId": 1,
  "reason": "상세 사유입니다"
}
```

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

**Query Parameters**

`universityId`, `authorNickname`, `status`, `deleted`, `keyword`, `page=0`, `size=20`

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
        "authorDeposit": 1,
        "status": "OPEN",
        "createdAt": "2026-06-22T12:30:00",
        "deleted": true,
        "deletedAt": "2026-06-22T12:30:00"
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
    "authorDeposit": 1,
    "content": "요청 내용입니다",
    "placeName": "홍길동",
    "meetAt": "2026-06-22T12:30:00",
    "authorNickname": "한끼친구",
    "createdAt": "2026-06-22T12:30:00",
    "deleted": true,
    "deletedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/admin/posts/{postId}/restore</code> - 게시글 복구</summary>

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

**Query Parameters**

`status`, `page=0`, `size=20`

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
    "processedAt": "2026-06-22T12:30:00",
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/reports/{reportId}/process</code> - 신고 처리</summary>

**Request Body**

```json
{
  "reportStatus": "PENDING",
  "comment": "상세 사유입니다"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "reportId": 1,
    "status": "PENDING",
    "isRewarded": true,
    "rewardPoint": 1000,
    "processedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/inquiries/{inquiryId}</code> - 문의 상세 조회</summary>

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
    "answerStatus": "PENDING",
    "answer": {},
    "createdAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/inquiries</code> - 문의 목록 조회</summary>

**Query Parameters**

`status`, `type`, `page=0`, `size=20`

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

**Request Body**

```json
{
  "content": "요청 내용입니다"
}
```

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
    "status": "SUBMITTED",
    "verificationStatus": "PENDING",
    "authorPlaceVerifiedAt": "2026-06-22T12:30:00",
    "applicantPlaceVerifiedAt": "2026-06-22T12:30:00",
    "submittedAt": "2026-06-22T12:30:00",
    "chatMessages": [
      {}
    ]
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/disputes</code> - 이의제기 목록 조회</summary>

**Query Parameters**

`status`, `page=0`, `size=20`

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

**Request Body**

```json
{
  "status": "SUBMITTED",
  "comment": "상세 사유입니다"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "status": "SUBMITTED",
    "adminComment": "상세 사유입니다",
    "refundedPoint": 1000,
    "processedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>PATCH /api/v1/admin/disputes/{disputeId}/override</code> - 이의제기 상태 변경</summary>

**Request Body**

```json
{
  "status": "SUBMITTED",
  "comment": "상세 사유입니다"
}
```

**Response Body**

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "disputeId": 1,
    "matchId": 1,
    "status": "SUBMITTED",
    "adminComment": "상세 사유입니다",
    "refundedPoint": 1000,
    "processedAt": "2026-06-22T12:30:00"
  }
}
```

</details>

<details>
<summary><code>GET /api/v1/admin/payments</code> - 결제 목록 조회</summary>

**Query Parameters**

`userId`, `status`, `page=0`, `size=20`

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
        "chargePoint": 1000,
        "amount": 1000,
        "payMethod": "string",
        "status": "READY",
        "cancelReason": "상세 사유입니다",
        "failReason": "상세 사유입니다",
        "createdAt": "2026-06-22T12:30:00",
        "completedAt": "2026-06-22T12:30:00",
        "cancelledAt": "2026-06-22T12:30:00"
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

**Query Parameters**

`page=0`, `size=20`

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
        "verificationStatus": "PENDING",
        "hostNickname": "한끼친구",
        "guestNickname": "한끼친구",
        "meetAt": "2026-06-22T12:30:00",
        "hasDispute": true,
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

**Query Parameters**

`cursorId`, `size=20`

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
        "title": "문의 제목입니다",
        "content": "요청 내용입니다",
        "domain": "MATCH",
        "relatedId": 1,
        "isRead": true,
        "readAt": "2026-06-22T12:30:00",
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

**Request Body**

요청 바디 없음

**Response Body**

```json
{
  "event": "notification",
  "data": {
    "type": "MATCH_CREATED",
    "title": "새 알림",
    "content": "알림 내용입니다"
  }
}
```

</details>

<details>
<summary><code>POST /api/v1/admin/ai/reports/chat/stream</code> - 관리자 AI 신고 SSE</summary>

**Request Body**

```json
{
  "conversationId": "string",
  "message": "string"
}
```

**Response Body**

```text
data: AI 응답 토큰

data: 다음 응답 토큰

```

</details>

<details>
<summary><code>POST /api/v1/admin/ai/console/chat/stream</code> - 관리자 AI 콘솔 SSE</summary>

**Request Body**

```json
{
  "conversationId": "string",
  "message": "string"
}
```

**Response Body**

```text
data: AI 응답 토큰

data: 다음 응답 토큰

```

</details>

## WebSocket / STOMP

| Type | Destination | Body | 설명 |
| --- | --- | --- | --- |
| Handshake | `/ws/chat` | - | SockJS fallback 지원 |
| Client publish | `/pub/chat/rooms/{chatRoomId}` | `ChatMessageRequestDto` | 채팅 메시지 전송 |
| Server subscribe | `/user/sub/chat/rooms/{chatRoomId}` | `ChatMessageResponseDto` | 사용자별 채팅 메시지 수신 |
| Server subscribe | `/user/queue/errors` | `String` | 채팅 오류 수신 |

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
