# 문혜린 트러블슈팅

한끼팟의 OTP 이메일 인증, AWS 배포, EC2 인프라 장애, Blue-Green 배포 과정에서 발생한 문제와 해결 과정을 정리한 문서입니다.

<a id="트러블슈팅-otp-구현"></a>

<details>
<summary><strong>트러블슈팅 — OTP 구현</strong></summary>

## 트러블슈팅 — OTP 구현

| 항목 | 내용 |
| --- | --- |
| 영역 | Backend / OTP 이메일 인증 |
| 심각도 | 🔴 High — 환경변수 주입 실패 시 OTP 전체 불동 |

---

## 🔴 이슈 1: `@EnableConfigurationProperties` 누락으로 OtpProperties 미동작

### 증상

`OtpProperties`에 `@ConfigurationProperties(prefix = "otp")`를 붙였는데 값이 주입되지 않거나 빌드 오류 발생.

### 원인

`@ConfigurationProperties`는 `@EnableConfigurationProperties`가 활성화되어 있어야 동작한다.
메인 애플리케이션 클래스에 해당 어노테이션이 없었음.

### 해결

`Team3FinalApplication.java`에 어노테이션 추가:

```java
@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties  // ← 추가
public class Team3FinalApplication { ... }
```

---

## 🔴 이슈 2: `.env` 파일 인라인 주석 오류

### 증상

환경변수 값이 의도한 대로 적용되지 않음.

### 원인

`.env` 파일에서 값 뒤에 인라인 주석을 작성함.

```bash
# ❌ 잘못된 예 — # 이후 내용이 값에 포함될 수 있음
JWT_SIGNUP_TOKEN_VALIDITY_TIME=300000   # 5분
```

### 해결

주석은 반드시 **별도 줄로 분리**.

```bash
# ✅ 올바른 예
# 단위: 밀리초(ms), 5분
JWT_SIGNUP_TOKEN_VALIDITY_TIME=300000
```

</details>

---

<a id="트러블슈팅-gmail-smtp-인증-실패-otp-메일-발송-불가"></a>

<details>
<summary><strong>트러블슈팅 — Gmail SMTP 인증 실패 (OTP 메일 발송 불가)</strong></summary>

## 트러블슈팅 — Gmail SMTP 인증 실패 (OTP 메일 발송 불가)

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-05-21 |
| 담당 | 문혜린 |
| 영역 | Backend / 이메일 인증 |
| 심각도 | 🔴 High — OTP 발송 전체 불가 |

---

## 🔍 문제 요약

포스트맨에서 OTP 이메일 발송 API를 호출하면 `200 OK`가 반환되지만, 실제 이메일이 수신되지 않음. 서버 로그에서는 Gmail 인증 실패 오류가 연속 발생.

---

## ❌ 오류 발생 순서

### 1차 오류 — Gmail 인증 실패

```
jakarta.mail.AuthenticationFailedException: 535-5.7.8 Username and Password not accepted.
https://support.google.com/mail/?p=BadCredentials
```

**발생 시점**: 서버 시작 시 Spring Actuator `MailHealthIndicator`가 메일 연결을 테스트하는 과정에서 발생.

### 2차 오류 — 동일 오류 재발

```
org.springframework.mail.MailAuthenticationException: Authentication failed
Caused by: jakarta.mail.AuthenticationFailedException: 535-5.7.8 Username and Password not accepted.
```

**발생 시점**: `OtpServiceImpl.sendOtp()` 비동기 실행 중 발생.

### 3차 오류 — 환경변수 로드 실패 (근본 원인 발견)

```
org.springframework.util.PlaceholderResolutionException:
Could not resolve placeholder 'MAIL_USERNAME' in value "${MAIL_USERNAME}"
```

**의미**: `MAIL_USERNAME` 환경변수 자체를 찾지 못함 → `.env` 파일이 로드되지 않고 있었음.

---

## 🔎 원인 분석

| 단계 | 의심 원인 | 실제 원인 여부 |
| --- | --- | --- |
| 1차 | Gmail 앱 비밀번호가 잘못되거나 만료됨 | 부분적 원인 |
| 2차 | Gmail 2단계 인증 미활성화 | 부분적 원인 |
| 3차 | `.env` 파일이 Spring에 로드되지 않음 | **근본 원인** ✅ |

**근본 원인 상세**:

`DotenvConfig.java`에서 `.env` 파일을 `./` (현재 작업 디렉토리) 기준으로 탐색하는데, IntelliJ의 실행 디렉토리(Working directory)가 프로젝트 루트와 다르게 설정되어 있어 `.env` 파일을 찾지 못하는 상황이었음.

---

## ✅ 해결 방법

### 방법 1: `.env` 파일 위치 확인

`.env` 파일은 반드시 `build.gradle`과 같은 **프로젝트 루트**에 위치해야 함.

```
Final-project/
├── .env            ← 여기에 있어야 함
├── build.gradle
├── gradlew
├── settings.gradle
└── src/
```

### 방법 2: IntelliJ 실행 디렉토리 확인

1. `Run` → `Edit Configurations`
2. `Team3FinalApplication` 선택
3. **Working directory** 항목을 프로젝트 루트로 설정
    - `$MODULE_WORKING_DIR$` 또는 절대 경로 입력

### 방법 3: IntelliJ 환경변수 직접 등록 ✅ (최종 해결)

`.env` 파일 로드 여부와 무관하게 IntelliJ에 환경변수를 직접 등록.

1. `Run` → `Edit Configurations`
2. `Environment variables` 항목 → 우측 폴더 아이콘 클릭
3. 아래 값 직접 입력:

```
MAIL_USERNAME=본인gmail@gmail.com
MAIL_PASSWORD=앱비밀번호16자리
```

1. 저장 후 서버 재시작

---

## 📬 Gmail 앱 비밀번호 발급 방법

Gmail SMTP를 사용하려면 일반 비밀번호가 아닌 **앱 비밀번호**가 필요함.

1. https://myaccount.google.com/security → **2단계 인증 활성화** (필수)
2. 앱 이름 입력 (예: `hankki-pot`) → 생성
3. 발급된 **16자리 비밀번호를 공백 없이** `.env` 또는 IntelliJ 환경변수에 입력

```bash
# ❌ 잘못된 예 (공백 포함)
MAIL_PASSWORD=abcd efgh ijkl mnop

# ✅ 올바른 예 (공백 제거)
MAIL_PASSWORD=abcdefghijklmnop
```

> ⚠️ Google Workspace(학교/회사 계정)는 관리자 정책에 따라 앱 비밀번호가 차단될 수 있음. 이 경우 개인 Gmail 계정으로 교체 필요.
> 

---

## 🛠️ 디버그 방법 — 환경변수 로드 확인

환경변수가 실제로 주입되는지 확인할 때 사용하는 임시 로그. **확인 후 반드시 삭제할 것.**

```java
// AuthServiceImpl.java 상단에 임시 추가
@Value("${spring.mail.username}")
private String mailUsername;

@Value("${spring.mail.password}")
private String mailPassword;

// sendEmailOtp() 메서드 안에 임시 로그
log.info("mail username: {}", mailUsername);
log.info("mail password length: {}", mailPassword != null ? mailPassword.length() : "NULL");
```

| 출력 결과 | 의미 |
| --- | --- |
| `password length: 16` | 값은 로드됨 → 비밀번호 자체가 틀린 것 |
| `password length: 0` 또는 `NULL` | `.env`가 로드되지 않은 것 |

---

## 🔒 보안 주의사항

- `.env` 파일은 반드시 `.gitignore`에 등록하여 GitHub에 올라가지 않도록 관리
- `.env`에 포함된 키가 외부 노출된 경우 즉시 재발급 필요
    - Gmail 앱 비밀번호
    - PortOne API 키
    - JWT Secret Key

</details>

---

<a id="트러블슈팅-aws-배포-기록-ec2-보안그룹corscrlfalb환경변수"></a>

<details>
<summary><strong>트러블슈팅 — AWS 배포 기록 : EC2 보안그룹·CORS·CRLF·ALB·환경변수</strong></summary>

## 트러블슈팅 — AWS 배포 기록 : EC2 보안그룹·CORS·CRLF·ALB·환경변수

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-06 |
| 환경 | AWS EC2 + Docker Compose + ALB |
| 영역 | 인프라 / 배포 |

---

## TS-01. EC2 보안 그룹 수정 오류

### 문제

8080 포트 소스를 `0.0.0.0/0` → ALB 보안 그룹으로 변경 시 에러 발생.

```
기존 IPv4 CIDR 규칙에 a 참조된 그룹 ID을(를) 지정할 수 없습니다.
```

### 원인

기존 규칙의 유형이 “사용자 지정 TCP”로 되어 있어서 소스 변경 불가.

### 해결

**기존 규칙 삭제** 후 새로 추가.

```
유형: 사용자 지정 TCP
포트: 8080
소스: sg-0844ae0672edbaea4 (hankkipot-alb-sg)
```

---

## TS-02. Vite 개발 서버 호스트 차단

### 문제

`https://hankkipot.cloud` 접속 시 에러 발생.

```
Blocked request. This host ("hankkipot.cloud") is not allowed.
To allow this host, add "hankkipot.cloud" to `server.allowedHosts` in vite.config.ts
```

### 원인

Vite 개발 서버가 보안상 외부 도메인 접근을 기본적으로 차단함.

### 해결

`frontend/vite.config.ts` 수정:

```tsx
server: {
  allowedHosts: [
    'app.dogpedia.store',
    'hankkipot.cloud', // 추가
  ],
},
```

### 핌샐 & 권장

Vite 개발 서버(`pnpm dev`)는 로친 개발용. 운영 환경에서는 `pnpm build` 후 정적 파일을 서빙하는 방식이 권장됨.

---

## TS-06. 프론트엔드 환경변수 미적용

### 문제

`https://hankkipot.cloud` 에서 API 요청이 `localhost:8080`으로 가는 현상.

### 원인 분석 과정

1. `frontend/.env.local` 에 `VITE_API_BASE_URL=http://43.201.250.191:8080` 로 설정되어 있었음
2. `frontend/.dockerignore` 에 `.env.local` 이 제외되어 컨테이너 안에 환경변수가 안 들어감
3. `docker-compose.prod.yml` 에서 `VITE_API_BASE_URL=${VITE_API_BASE_URL_PROD}` 로 주입하고 있었으나 루트 `.env` 값이 잘못됨

### 해결

**1단계**: `frontend/.env.local` 수정

```
VITE_API_BASE_URL=https://hankkipot.cloud
```

**2단계**: `docker-compose.prod.yml` environment 하드코딩

```yaml
environment:
  - VITE_API_BASE_URL=https://hankkipot.cloud
```

**3단계**: 재빌드

```bash
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up --build -d
```

### 핵심 개념

- **Vite 환경변수**: `VITE_` 접두사가 붙은 변수만 클라이언트에 노출됨
- **빌드 시점 주입**: `pnpm dev`는 런타임에 `.env`를 읽지만, `pnpm build`는 빌드 시점에 환경변수를 번들에 포함시킴
- **`.dockerignore`**: Docker 빌드 컨텍스트에서 제외할 파일 목록. `.env`이 여기 있으면 컨테이너 안에 안 들어감

---

## TS-03. ALB 라우팅 규칙 누락

### 문제

- `https://hankkipot.cloud/actuator/health` 접속 시 프론트엔드 404 페이지 반환
- WebSocket(`/ws/*`) 연결 실패

### 원인

ALB 리스너 규칙에 `/api/*` 만 있고 `/actuator/*`, `/ws/*` 규칙이 없어서 기본값인 프론트엔드로 라우팅됨.

### 해결

ALB HTTPS:443 리스너에 규칙 추가:

| 우선순위 | 경로 | 대상 그룹 |
| --- | --- | --- |
| 1 | /api/* | hankkipot-tg (8080) |
| 2 | /actuator/* | hankkipot-tg (8080) |
| 3 | /ws/* | hankkipot-tg (8080) |

---

## TS-04. Windows CRLF 줄바꾸으로 인한 CI/CD 실패

### 문제

CI/CD 실행 시 에러 발생:

```
err: ./.env: line 6: $'\r': command not found
Process exited with status 127
```

### 원인

Windows에서 `.env` 파일 저장 시 줄바꾸이 `CRLF(\r\n)` 로 저장됨. Linux(EC2)는 `LF(\n)` 만 인식하므로 `\r` 를 명령어로 잘못 해석.

### 해결

**EC2에서 즉시 수정:**

```bash
sed -i 's/\r//' /home/ec2-user/app/.env
```

**근본 해결 (IntelliJ):** 파일 → 설정 → 편집기 → 코드 스타일 → 줄 구분자 → `Unix and macOS (\n)` 선택

**Git 자동 변환 비활성화:**

```bash
git config --global core.autocrlf false
```

---

## TS-5. api.hankkipot.cloud 서브도메인 미설정

### 문제

로그인 시 에러 발생:

```
Request URL: https://api.hankkipot.cloud/api/v1/auth/login
net::ERR_NAME_NOT_RESOLVED
```

### 원인

`.env` 파일의 `VITE_API_BASE_URL_PROD=https://api.hankkipot.cloud` 가 `frontend/.env.local` 의 수정값으로 덮어쓌워지면서 `api.hankkipot.cloud` 서브도메인으로 요청이 가고 있었음. Route 53에 `api.hankkipot.cloud` A 레코드가 없어서 DNS 해석 실패.

### 해결

**방법 1 (권장): Route 53에 서브도메인 추가**

```
레코드 이름: api
유형: A
별칭: 켜기
ALB: hankkipot-alb
```

**방법 2: 환경변수 통일**

```
VITE_API_BASE_URL_PROD=https://hankkipot.cloud
```

### 히승 & 재발 방지

- CI/CD 배포 시 EC2에 있던 `.env.local` 이 덮어쓌워질 수 있음
- `.env` 파일은 git에 올라가지 않으므로 **팀원 간 직접 공유 필요**
- EC2에 수동으로 수정한 파일은 다음 배포 시 덮어쓌워질 수 있으므로 반드시 git에 반영해야 함

</details>

---

<a id="트러블슈팅-blue-green-무중단-배포-iam-권한mysql-컨테이너-잔존"></a>

<details>
<summary><strong>트러블슈팅 — Blue-Green 무중단 배포 (IAM 권한·mysql 컨테이너 잔존)</strong></summary>

## 트러블슈팅 — Blue-Green 무중단 배포 (IAM 권한·mysql 컨테이너 잔존)

| 항목 | 내용 |
| --- | --- |
| 환경 | AWS EC2 (t4g.medium), Docker Compose, ALB |
| 영역 | 인프라 / CI/CD |
| 심각도 | 🔴 High — 무중단 배포 파이프라인 전체 영향 |

---

## TS-1. AWS CLI 권한 부족 (AccessDenied)

### 문제

배포 스크립트에서 ALB 규칙을 조회하려고 하자:

```
An error occurred (AccessDenied) when calling the DescribeRules operation:
User: arn:aws:sts::...:assumed-role/hankkipot-ec2-role/... is not authorized
to perform: elasticloadbalancing:DescribeRules
```

### 원인

EC2 인스턴스에 연결된 IAM 역할(`hankkipot-ec2-role`)에 ELB 관련 권한이 없었음.

### 해결

IAM → 역할 → `hankkipot-ec2-role` → 인라인 정책 생성:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "elasticloadbalancing:DescribeRules",
        "elasticloadbalancing:ModifyRule",
        "elasticloadbalancing:DescribeTargetGroups",
        "elasticloadbalancing:DescribeTargetHealth"
      ],
      "Resource": "*"
    }
  ]
}
```

정책 이름: `hankkipot-alb-deploy-policy`

### 핵심 개념

- **IAM 역할(Role)**: EC2가 AWS 리소스에 접근할 때 사용하는 권한 묶음
- **최소 권한 원칙**: 필요한 액션(`Describe*`, `ModifyRule`)만 허용

---

## TS-2. EC2에 남아있는 사용하지 않는 mysql 컨테이너

### 문제

`docker-compose -f docker-compose.prod.yml ps` 결과에 `hankkipot-mysql`(생성 2 days ago)이 계속 떠있음. 로컬 `docker-compose.prod.yml`에는 이미 `mysql:` 서비스가 없는데도 EC2에서는 사라지지 않음.

### 원인

RDS로 DB를 이전하면서 compose 파일에서 `mysql:` 서비스를 제거했지만, 이미 실행 중이던 컨테이너는 `docker-compose up`이 자동으로 정리하지 않음.

compose 파일은 **"이 서비스들을 이렇게 유지해라"** 는 선언일 뿐, 파일에서 빠진 기존 컨테이너를 적극적으로 삭제하지는 않음.

### 해결

**1단계: 정리 전 검증 — RDS 마이그레이션이 실제로 완료됐는지 확인**

```bash
docker exec -it hankkipot-app-green env | grep DB_URL_PROD
mysql -h <RDS주소> -u admin -p hankkipot -e "SHOW TABLES;"
```

→ 백엔드가 RDS를 가리키고, RDS에 32개 테이블이 모두 존재함을 확인

**2단계: 컨테이너 및 볼륨 제거**

```bash
docker stop hankkipot-mysql
docker rm hankkipot-mysql
docker volume ls | grep mysql      # app_mysql_data 확인
docker volume rm app_mysql_data
```

### 교훈

- compose 파일에서 서비스를 제거하는 작업은 **"새로 안 만든다"** 는 의미일 뿐, **"기존 것을 지운다"** 는 의미가 아님
- 데이터 볼륨 삭제는 되돌릴 수 없으므로, 마이그레이션 완료 여부를 반드시 먼저 검증해야 함
- `docker volume ls | grep <키워드>` 로 관련 볼륨을 미리 확인하는 습관이 필요함

---

## 📌 자주 쓰는 명령어 모음

### ALB 규칙 확인

```bash
aws elbv2 describe-rules \
  --listener-arn <리스너ARN> \
  --query "Rules[].{Priority:Priority, Path:Conditions[0].Values, TargetGroups:Actions[0].ForwardConfig.TargetGroups}" \
  --output table
```

### 특정 우선순위 규칙만 JSON으로 확인

```bash
aws elbv2 describe-rules --listener-arn <리스너ARN> --query "Rules[?Priority=='3']" | cat
```

### Blue-Green 전환 실행

```bash
cd /home/ec2-user/app
chmod +x deploy-blue-green.sh
./deploy-blue-green.sh
```

### 컨테이너 상태 및 로그 확인

</details>

---

<a id="ec2-인프라-장애-트러블슈팅"></a>

<details>
<summary><strong>EC2 인프라 장애 트러블슈팅</strong></summary>

## EC2 인프라 장애 트러블슈팅

> 6개의 장애가 **디스크 고갈 → 메모리 고갈 → DB 이전 → IP 고정** 순서로 연쇄 발생했습니다.
각 장애는 독립적으로 보이지만 근본 원인은 **단일 EC2(t4g.medium)에 11개 컨테이너를 밀어넣은 구조** 에서 출발합니다.
> 

---

## 장애 연쇄 흐름

```jsx
CI/CD 빌드 반복
    ↓
도커 이미지·레이어 누적
    ↓
디스크 100% 고갈
    ↓
OTP 500 에러 / Kafka 컨테이너 소멸
    ↓
메모리 OOM
    ↓
SSH 접속 불가 / ALB 전체 unhealthy
    ↓
EC2 강제 재기동 → IP 변경
    ↓
GitHub Actions 배포 파이프라인 실패
```

---

## 1. 기존 문제

### SSH 및 EC2 직렬 콘솔 전체 무응답

- 로컬 PC 재부팅 후 EC2 SSH 접속 불가
- AWS 콘솔 Instance Connect · Session Manager · 직렬 콘솔 모두 무응답
- EC2 상태는 **"실행 중 / 상태 검사 3/3 통과"** 로 정상 표시 → 원인 파악이 어려웠음

### Windows 한글 경로에서 SSH 키 파일 인식 실패

- Git Bash에서 `.pem` 키 파일이 로드되지 않음
- `chmod 400`, 경로 변경 등 모든 방법 시도했으나 동일하게 실패
- 경로가 `\353\254\270\355\230\234\353\246\260` 형태의 UTF-8 8진수로 표시됨

### 디스크 100% 고갈 → OTP 500 에러 + Kafka 컨테이너 소멸

- 회원가입 OTP 발송 시 `500 Internal Server Error`
- 채팅·알림 전체 수신 불가
- `docker ps`에서 `hankkipot-kafka` 컨테이너 자체가 사라짐

### 메모리 OOM → 504 Gateway Timeout + 서버 전체 응답 불가

- `https://hankkipot.cloud` 접속 시 정적 파일(`favicon.ico`)조차 504
- ALB 대상 그룹 전체 **unhealthy**
- EC2 상태는 정상이나 SSH·직렬 콘솔 모두 무응답 (SSH 접속 불가와 동일 증상 재발)

### EC2 내부 MySQL 컨테이너와 RDS 이중 운영

- `docker-compose.prod.yml`이 EC2 로컬 MySQL 컨테이너에 연결된 상태
- 생성해뒀던 RDS 인스턴스(`hankkipot-db`)는 미사용
- MySQL이 EC2 메모리를 추가로 점유 → 이전 단계의 직접 원인 중 하나

### EC2 재기동 시 퍼블릭 IP 변경 → 배포 파이프라인 실패

- 강제 중지 후 재시작 시 퍼블릭 IP 변경
- GitHub Actions SCP 단계에서 `i/o timeout` 에러
- 매 재기동마다 GitHub Secrets의 `EC2_HOST` 값을 수동으로 갱신해야 했음

---

## 2. 판단 기준

### 콘솔까지 무응답이면 OS 레벨 리소스 고갈 의심

SSH뿐 아니라 AWS 콘솔 직렬 콘솔까지 무응답인 상황은 네트워크 문제가 아니라 **OS 자체가 응답 불가 상태**임을 의미합니다. EC2 상태 검사가 통과해도 메모리·디스크 고갈로 OS가 멈출 수 있다는 점을 기준으로 삼았습니다.

### SSH 클라이언트 교체로 경로 문제 우회

Git Bash의 OpenSSH는 비-ASCII 경로를 처리하지 못합니다. Windows에 기본 내장된 **PowerShell OpenSSH**는 이 제약이 없으므로, 재설치나 환경 수정 없이 클라이언트만 바꾸는 것이 가장 빠른 해결책이라고 판단했습니다.

### 디스크 고갈 근본 원인은 도커 이미지 누적

`df -h` 한 줄로 디스크 100%를 확인했고, CI/CD가 빌드될 때마다 이전 이미지·중간 레이어·종료된 컨테이너가 쌓이는 구조임을 파악했습니다. 일회성 정리가 아니라 **자동화된 주기적 정리**가 필요하다고 판단했습니다.

### 스왑 부재 + 컨테이너 11개 동시 구동이 OOM의 직접 원인

재기동 후 `free -h`로 확인한 결과 스왑이 0이었고, 가용 메모리 155MB에서 시스템 전체가 멈춘 것이 확인됐습니다. 즉각적인 스왑 추가와 함께, **MySQL을 EC2에서 RDS로 이전**하는 근본 조치가 병행되어야 한다고 판단했습니다.

### RDS 전환으로 메모리 확보 + 인프라 정합성 동시 해결

이미 RDS 인스턴스가 생성된 상태였으므로 추가 비용 없이 전환 가능했습니다. EC2 장애 시 DB 데이터가 같이 사라지는 위험도 제거할 수 있어 운영 안정성 측면에서도 전환이 명확한 선택이었습니다.

### Elastic IP로 재기동과 IP를 분리

재기동마다 수동으로 Secrets을 갱신하는 것은 휴먼 에러를 유발합니다. **Elastic IP 할당**으로 IP 고정 자체를 없애는 것이 가장 깔끔한 해결책이라고 판단했습니다.

---

## 3. 적용 방식

### EC2 강제 중지 → 재시작

```jsx
AWS 콘솔 → EC2 → 인스턴스 상태 → 강제 중지 → 시작
```

OS가 재기동되며 정상화. SSH 접속 복구 확인 후 디스크·메모리 조치 순서로 근본 원인 조치.

---

### Git Bash → PowerShell OpenSSH로 전환

powershell

```jsx
# PowerShell에서 실행 (영문 경로에 키 파일 위치)
ssh -i "C:\Java_sp\.ssh\hankkipot-key.pem" ec2-user@<EC2_IP>
```

키 파일 저장 위치를 영문 경로(`C:\Java_sp\.ssh\`)로 통일하고, 팀 온보딩 문서에 명시.

---

### 도커 불필요 리소스 일괄 정리 + 크론탭 자동화

bash

```jsx
# 즉시 정리 (100% → 76%, 약 7.3GB 확보)
docker system prune -af

# Kafka 재기동
docker-compose -f docker-compose.prod.yml up -d kafka
docker restart hankkipot-app
```

하루 만에 디스크가 다시 압박받아 정리 주기를 단축:

bash

```jsx
# 매주 일요일 → 매일 새벽 3시로 변경
echo '0 3 * * * docker system prune -af >> /home/ec2-user/docker-prune.log 2>&1' | crontab -
```

---

### 스왑 2GB 즉시 추가 + RDS 이전 병행

bash

```jsx
# 스왑 파일 생성 및 영구 마운트
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

결과: `Swap: 2.0Gi / used: 0.0Ki / free: 2.0Gi` → 즉시 안정화. 근본 조치는 MySQL → RDS 이전으로 병행.

---

### EC2 MySQL → RDS 데이터 이전 및 전환

**1단계. MySQL 클라이언트 설치** (AL2023 기본 저장소에 mysql 패키지 없음 → MariaDB 클라이언트로 대체)

bash

```jsx
sudo dnf install -y mariadb105
```

**2단계. EC2 MySQL 컨테이너 데이터 덤프**

bash

```jsx
# --no-tablespaces 미적용 시 PROCESS privilege 에러 발생
docker exec hankkipot-mysql mysqldump \
  -u hankipot_user -ppassword123! \
  --no-tablespaces hankkipot > hankkipot_dump.sql
```

**3단계. RDS로 import** (32개 테이블 정상 이전 확인)

**4단계. `docker-compose.prod.yml` 수정**

yaml

```jsx
# 제거 항목
# - mysql 서비스 전체
# - app.depends_on.mysql
# - volumes.mysql_data
```

**5단계. `.env` DB 접속 정보를 RDS 엔드포인트로 교체 후 재배포**

---

### Elastic IP 할당으로 IP 고정

```jsx
AWS 콘솔 → EC2 → Elastic IP → 할당 → EC2 인스턴스에 연결
```

GitHub Secrets의 `EC2_HOST`를 Elastic IP로 1회 갱신 후 이후 재기동과 무관하게 고정.

---

## 4. 개선 효과

| 항목 | 개선 전 | 개선 후 |
| --- | --- | --- |
| 디스크 사용률 | 100% (여유 20KB) | 76% → 매일 자동 정리로 유지 |
| 스왑 메모리 | 0B | 2GB 확보 |
| EC2 메모리 여유 | ~155MB (OOM 직전) | MySQL 제거로 약 400MB 추가 확보 |
| MySQL 운영 방식 | EC2 컨테이너 (장애 시 데이터 소멸 위험) | RDS 관리형 (자동 백업·failover) |
| SSH 접속 방법 | Git Bash (한글 경로 불가) | PowerShell로 통일 |
| EC2 IP | 재기동마다 변경 → Secrets 수동 갱신 필요 | Elastic IP 고정 → 자동화 완전 복구 |
| Kafka 장애 | 디스크 풀로 컨테이너 소멸 | 디스크 자동 정리로 재발 방지 |
| 배포 파이프라인 | IP 변경마다 수동 개입 필요 | 재기동과 무관하게 자동 배포 유지 |

### 한계 및 향후 검토

- 스왑은 디스크 기반이라 RAM보다 느림 → 부하 테스트(K6) 수행 전 **t4g.medium → t4g.large 업그레이드** 권장
- 스왑 파일 2GB가 디스크 여유분을 추가 점유 → 이전 단계의 매일 자동 정리와 함께 모니터링 필요
- 장기적으로는 컨테이너 11개를 단일 EC2에서 운영하는 구조 자체의 개선 필요

</details>

---

<a id="트러블슈팅-aws-배포-기록-디스크메모리kafkaopenaih2폴백"></a>

<details>
<summary><strong>트러블슈팅 — AWS 배포 기록 : 디스크·메모리·Kafka·OpenAI·H2폴백</strong></summary>

## 트러블슈팅 — AWS 배포 기록 : 디스크·메모리·Kafka·OpenAI·H2폴백

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-06-09 |
| 환경 | AWS EC2 (t4g.medium), Docker Compose, GitHub Actions |
| 영역 | 인프라 / 배포 |

---

## TS-1. AWS 보안그룹 규칙 설명 한글 불가

### 문제

보안그룹 규칙 Description 칸에 한글 입력 시 에러 발생.

```
Invalid rule description. Valid descriptions are strings less than 256 characters
from the following set: a-zA-Z0-9. _-:/()#,@[]+=&;{}!$*
```

### 원인

AWS 보안그룹 규칙 설명은 영문/숫자/특수문자만 허용. 한글 불가.

### 해결

설명칸을 비워두거나 영문으로만 입력.

---

## TS-2. EC2 스토리지 용량 부족

### 문제

Docker Compose로 빌드 중 에러 발생하며 빌드 실패.

```
no space left on device
```

### 원인

EC2 기본 스토리지 8GiB로 Docker 이미지 빌드 불가.

### 해결

AWS 콘솔 → EC2 → 스토리지 탭 → 볼륨 수정 → 30GiB로 변경 후 파티션 확장.

```bash
sudo growpart /dev/nvme0n1 1
sudo xfs_growfs /
```

---

## TS-3. EC2 인스턴스 사양 부족으로 먹통

### 문제

Docker Compose 빌드 중 EC2가 완전히 먹통. SSH 접속 불가.

### 원인

`t4g.small` (vCPU 2, 메모리 2GB)으로는 Spring Boot + MySQL + Redis + Kafka + PostgreSQL + Frontend 동시 빌드 불가. 메모리 100% 도달 → SSH 데스노 포함 모든 프로세스 중단.

### 해결

EC2 인스턴스 유형을 `t4g.small` → `t4g.medium` (메모리 4GB)으로 변경.

> ⚠️ Docker Compose로 여러 서비스를 띄우는 경우 처음부터 **t4g.medium** 이상 선택 권장.
> 

---

## TS-4. EC2 재시작 후 퍼블릭 IP 변경

### 문제

EC2 중지/시작 후 퍼블릭 IP가 변경되어 SSH 접속 불가.

### 원인

EC2는 재시작할 때마다 퍼블릭 IP가 동적으로 변경됨.

### 해결

EC2 콘솔에서 새 퍼블릭 IP 확인 후 SSH 접속 및 GitHub Secrets `EC2_HOST` 업데이트.

> 💡 고정 IP가 필요하면 Elastic IP를 할당하면 됨.
> 

---

## TS-5. ‘.pem’ 키 파일 경로 오류 (한글 경로)

### 문제

```
chmod: cannot access '/c/Users/문혜린/Downloads/hankipot-key.pem': No such file or directory
```

### 원인

Git Bash / IntelliJ 터미널에서 한글 폴더명 인식 불가.

### 해결

`.pem` 파일을 한글 없는 경로로 이동.

```
C:\Java_sp\hankkipot-key.pem
```

터미널에서는 `/c/Java_sp/hankkipot-key.pem` 으로 접근.

---

## TS-6. Docker buildx 버전 부족

### 문제

```
compose build requires buildx 0.17.0 or later
```

### 원인

EC2에 설치된 Docker buildx 버전이 낙음.

### 해결

```bash
mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-arm64 \
  -o ~/.docker/cli-plugins/docker-buildx
chmod +x ~/.docker/cli-plugins/docker-buildx
```

---

## TS-7. H2 DB로 실행되는 문제

### 문제

prod 프로파일로 실행했는데 MySQL 대신 H2 메모리 DB로 연결됨.

```
HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:...
```

### 원인 (복합)

1. `docker-compose.yml` environment 블록의 `DB_URL`에 `${MYSQL_DATABASE}` 변수 참조 → `.env`보다 environment 블록이 우선순위 높아서 치환 안 됨
2. `application-prod.yml`이 `.gitignore`에 등록되어 EC2에 파일이 없음
3. `application-prod.yml` 없으면 datasource 설정이 없으므로 Spring Boot 기본값인 H2로 폴백

### 해결

**① `docker-compose.yml` environment 블록에서 `DB_URL` 하드코딩**

```yaml
DB_URL: jdbc:mysql://mysql:3306/hankkipot?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul
```

**② `application-prod.yml` EC2에 직접 전송 (scp)**

```bash
scp -i /c/Java_sp/hankkipot-key.pem \
  /c/Java_sp/Final-project/src/main/resources/application-prod.yml \
  ec2-user@{EC2_IP}:/home/ec2-user/app/src/main/resources/application-prod.yml
```

**③ `.env` 파일 수정 (로친 → EC2용 값으로)**

```
MYSQL_DATABASE=hankkipot       # hankipot → hankkipot (오타 수정)
REDIS_HOST=redis               # localhost → redis (컨테이너 서비스명)
KAFKA_BOOTSTRAP_SERVERS=kafka:29092  # localhost → kafka
```

---

## TS-8. Kafka Consumer group-id 플레이스홀더 오류

### 문제

```
Could not resolve placeholder 'spring.kafka.consumer.group-id' in value "${spring.kafka.consumer.group-id}-dlq"
```

### 원인

`DlqEventConsumer`에서 `${spring.kafka.consumer.group-id}-dlq`를 참조하는데, 환경변수로 주입이 안 됨.

### 해결

`docker-compose.yml` app 서비스 environment에 추가:

```yaml
SPRING_KAFKA_CONSUMER_GROUP_ID: hankkipot-group
```

---

## TS-9. OpenAI API Key 주입 오류

### 문제

```
OpenAI API key must be set. Use the connection property: spring.ai.openai.api-key
```

### 원인

Spring AI는 `SPRING_AI_OPENAI_API_KEY` 환경변수명으로 읽는데, `.env`에는 `OPEN_AI_KEY`로 정의되어 있어 매핑 안 됨.

### 해결

`docker-compose.yml` app 서비스 environment에 추가:

```yaml
SPRING_AI_OPENAI_API_KEY: ${OPEN_AI_KEY}
```

---

## TS-10. VectorStore 빈 충돌

### 문제

```
Cannot register bean definition for bean 'vectorStore' since there is already one defined
```

### 원인

Spring AI pgvector 자동설정과 커스텀 `AiRagVectorStoreConfig`가 동시에 `vectorStore` 빈 생성 시도.

### 해결

`application-prod.yml`에 pgvector 자동설정 제외 추가:

```yaml
spring:
  autoconfigure:
    exclude: org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration
```

---

## TS-11. GitHub Actions scp-action exclude 파라미터 오류

### 문제

```
Warning: Unexpected input(s) 'exclude'
```

### 원인

`appleboy/scp-action@v0.1.7`은 `exclude` 파라미터를 지원하지 않음.

### 해결

`deploy.yml`에서 `exclude` 파라미터 제거.

---

## TS-12. GitHub Actions SSH 접속 타임아웃

### 문제

```
dial tcp ***:22: i/o timeout
```

### 원인

EC2 보안그룹 SSH(22) 포트가 `내 IP`만 허용되어 있어 GitHub Actions 서버 IP 차단됨.

### 해결

보안그룹 SSH(22) 소스를 `0.0.0.0/0`으로 변경.

---

## TS-13. CORS 오류

### 문제

```
Access to XMLHttpRequest has been blocked by CORS policy:
No 'Access-Control-Allow-Origin' header is present
```

### 원인

`.env`의 `CORS_ALLOWED_ORIGINS`가 `localhost`만 허용하고 있어 EC2 IP에서 오는 요청 차단.

### 해결

`.env` 파일 수정:

```
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://localhost:8090,http://43.201.250.191:5173
```

---

## TS-14. 프론트엔드 API URL 환경변수 미반영

### 문제

프론트엔드에서 API 요청이 `localhost:8080`으로 가는 문제.

### 원인

- `docker-compose.yml` environment 블록의 `VITE_API_BASE_URL`이 Vite 개발 서버에 주입이 안 됨
- Vite는 `.env` 또는 `.env.local` 파일에서 환경변수를 읽음
- `.env.local`이 `.gitignore`에 등록되어 EC2에 없었음

### 해결

프론트엔드 `.env.local` 파일을 scp로 EC2에 전송:

```bash
scp -i /c/Java_sp/hankkipot-key.pem \
  /c/Java_sp/Final-project/frontend/.env.local \
  ec2-user@{EC2_IP}:/home/ec2-user/app/frontend/.env.local
```

---

## 📎 EC2에 수동으로 올려야 하는 파일 목록

</details>

---

<a id="트러블슈팅-ec2-서버-장애-및-인프라-개선"></a>

<details>
<summary><strong>트러블슈팅 — EC2 서버 장애 및 인프라 개선</strong></summary>

## 트러블슈팅 — EC2 서버 장애 및 인프라 개선

| 항목 | 내용 |
| --- | --- |
| 작성일 | 2026-06-14 |
| 환경 | AWS EC2 (t4g.medium), Docker Compose, GitHub Actions |
| 영역 | 인프라 / 서버 장애 |

---

## TS-01. SSH 및 EC2 직렬 콘솔 접속 불가

### 문제

- 로컬 PC에서 백신/구라제거기 실행 후 재부팅 → EC2 SSH 접속 불가
- AWS 콘솔의 EC2 Instance Connect, Session Manager, 직렬 콘솔 모두 무응답
- EC2 인스턴스 상태는 “실행 중 / 상태 검사 3/3 통과”로 정상 표시

### 원인 분석

1. 1차 의심: 로컈 네트워크 스택 손상 (Winsock 등) — `netsh winsock reset` 등 시도했으나 해결 안 됨
2. **쳨종 원인: EC2 인스턴스 자체의 리소스(메모리/디스크) 고갈로 OS가 응답 불가 상태**가 되어 SSH 데스놈, SSM 에이전트, 직렬 콘솔 입력까지 전부 멈춴
    - 인스턴스 상태 검사는 ”하드웨어/네트워크 레벨“ 체크라 서 OS 내부 응답 불가와는 별개로 통과될 수 있음

### 해결

AWS 콘솔 → EC2 → 인스턴스 상태 → **강제 중지 → 시작**

재기동 후 OS가 정상화되며 SSH 접속 가능해짐.

### 재발 방지

- 근본 원인(디스크/메모리 고갈)을 해소해야 동일 증상 재발 방지 (TS-03, TS-04 참고)
- 인스턴스가 “정상”으로 보여도 SSH가 안 되면 **리소스 고갈을 우선 의심**할 것

---

## TS-02. Windows에서 SSH 키 파일(.pem) 인식 실패

### 문제

```
debug1: no pubkey loaded from C:/Java_sp/.ssh/hankkipot-key.pem
debug1: identity file ... type -1
```

Git Bash에서 `chmod 400`, 경로 변경(`~/.ssh`, `/c/ssh/` 등) 모두 시도했으나 동일하게 실패.

### 원인 분석

- 키 파일 경로 또는 사용자 홈 디렉토리에 **한글 문자**(`C:/Users/문혜린/...`)가 포함됨
- Git Bash의 OpenSSH 클라이언트가 비-ASCII 경로를 처리하지 못해 키 파일을 읽지 못함
- 실제 변환 시도 시 경로가 `\353\254\270\355\230\234\353\246\260` 같은 UTF-8 8진수 이스케이프로 표시됨

### 해결

**PowerShell의 OpenSSH 클라이언트** (`OpenSSH_for_Windows_9.5p2`)로 전환:

```powershell
ssh -i "C:\Java_sp\.ssh\hankkipot-key.pem" ec2-user@<EC2_IP>
```

영문 경로(`C:\Java_sp\.ssh\`)에 키 파일을 위치시킴.

### 재발 방지

"Windows + 한글 사용자명 환경에서는 SSH/SCP를 PowerShell + 영문 경로로 실행" 가이드 팀 문서에 명시. 새 팀원 온보딩 시 키 파일 저장 위치를 영문 경로로 통일.

---

## TS-03. 디스크 100% 사용으로 인한 OTP 500 및 Kafka 컨테이너 소실

### 문제

1. 회원가입 OTP 발송 시 `500 Internal Server Error`
2. 채팅/알림이 전혀 수신되지 않음

### 원인 분석

- `df -h` 결과 `/` 파티션 **100% 사용** (여유 20KB)
- Spring Boot Actuator 로그에 `DiskSpaceHealthIndicator: Free disk space ... below threshold` 지속 발생
- OTP 메일 발송 시도 시: 디스크 부족으로 JVM이 SSL 핸드쉘이크에 필요한 임시 파일/버퍼를 정상 처리하지 못해 발생:

```
jakarta.mail.MessagingException: Could not connect to SMTP host: smtp.naver.com, port: 465
Caused by: SSLHandshakeException: Remote host terminated the handshake
Caused by: EOFException: SSL peer shut down incorrectly
```

- `docker ps`에서 `hankkipot-kafka` 컨테이너 자체가 사라짘 (`No such container`) → Kafka는 디스크에 토픽 로그 세그먼트를 지속적으로 기록하는데, 디스크 풀 상태에서 쓰기 실패 → 컨테이너 비정상 종료
- **근본 원인**: CI/CD로 도커 이미지를 빌드할 때마다 이전 이미지·중간 레이어·종료된 컨테이너가 누적되어 디스크를 잠식

### 해결

```bash
docker system prune -af   # 100% → 76% (약 7.3GB 확보)

# Kafka 재기동
docker-compose -f docker-compose.prod.yml up -d kafka
docker restart hankkipot-app   # Kafka 재연결 위해 앱 재시작
```

### 재발 방지

크론탭으로 `docker system prune -af` 자동 실행 등록:

```bash
# 하루 만에 디스크가 다시 압박받는 사례 발생하여 기존 매주 일요일 새벽 3시 → 매일 새벽 3시로 단축
echo '0 3 * * * docker system prune -af >> /home/ec2-user/docker-prune.log 2>&1' | crontab -
```

---

## TS-04. 메모리 부족(OOM)으로 인한 504 Gateway Timeout 및 서버 전체 응답 불가

### 문제

- `https://hankkipot.cloud` 접속 시 정적 파일(`favicon.ico`)조차 `504 Gateway Timeout`
- ALB 대상 그룹 모두 **비정상(unhealthy)**
- EC2 인스턴스 상태는 정상이나 SSH/직렬 콘솔 모두 무응답 (TS-01과 동일 증상)

### 원인 분석

재기동 후 리소스 확인:

```
Mem:  total 3.7Gi / used 2.6Gi / free 155Mi / available 997Mi
Swap: 0B
Disk: 82% (5.6GB 여유) ← 디스크는 정상, 디스크 문제 아님
```

- t4g.medium(메모리 4GB) 한 대에서 **컨테이너 11개**(MySQL, Redis, Kafka, Spring Boot, 프론트엔드, Grafana, Prometheus, Loki, Alloy, n8n, pgvector)가 동시 구동
- **스왓 메모리가 0**인 상태에서 가용 메모리(997Mi)가 한계에 도달 → OOM으로 시스템 전체가 응답 불가 상태에 빠짐
- ALB 헬스체크조차 응답받지 못해 대상 그룹이 unhealthy로 전환 → 502/504 발생

### 해결 (1차 — 즉시 적용)

스왓 2GB 추가:

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

결과: `Swap: 2.0Gi used: 0.0Ki free: 2.0Gi`

### 해결 (2차 — 근본 조치)

MySQL을 EC2 컨테이너에서 **AWS RDS로 이전**하여 EC2 메모리에서 MySQL 프로세스 자체를 제거 (TS-05 참고)

### 한계 및 향후 검토

- 스왓은 디스크 기반이라 RAM보다 훨씬 느림 → 메모리 압박이 잔으면 에응답 지연(스왓 스래싱) 재발 가능
- 부하 테스트(K6) 수행 전에는 **인스턴스 사이즈업(t4g.medium → t4g.large)** 권장
- 스왓파일 2GB가 디스크 여유분을 추가로 점유하므로 TS-03의 정리 주기(매일)와 함께 모니터링 필요

---

## TS-05. RDS 미연결 상태에서 EC2 내부 MySQL 컨테이너 이중 운영

### 문제

- 기획서/SA 문서상 DB는 AWS RDS로 설계되어 있었음
- 실제로는 `docker-compose.prod.yml`의 `mysql` 컨테이너(EC2 로컈)에 연결되어 있었고, 이미 생성된 RDS 인스턴스(`hankkipot-db`)는 미사용 상태
- EC2 메모리 자원을 MySQL이 추가로 소비 (TS-04의 원인 중 하나)

### 해결 — RDS로 전환

**1. RDS 접속 확인 및 MySQL 클라이언트 대체 패키지 설치**

```bash
# AL2023 기본 저장소에 mysql 공식 클라이언트 패키지가 없어 MariaDB 클라이언트(프로토콜 호환)로 대체
sudo dnf install -y mariadb105
```

**2. 기존 EC2 MySQL 컨테이너 데이터 덤프**

```bash
docker exec hankkipot-mysql mysqldump -u hankipot_user -ppassword123! --no-tablespaces hankkipot > hankkipot_dump.sql
```

> `--no-tablespaces` 미적용 시 `Access denied; PROCESS privilege` 에러 발생
> 

**3. RDS로 import (32개 테이블 정상 이전 확인)**

**4. `docker-compose.prod.yml`에서 `mysql` 서비스, `app.depends_on.mysql`, `volumes.mysql_data` 제거**

**5. `.env`의 DB 접속 정보를 RDS 엔드포인트/계정 정보로 교체**

### 재발 방지 / 효과

- EC2에서 MySQL 컨테이너 제거로 메모리 여유 확보 (TS-04 완화)
- 기획서(인프라 구성)와 실제 운영 환경의 정합성 확보
- EC2 장애 시에도 DB 데이터는 영향받지 않음

---

## TS-05. EC2 재기동 시 퍼블릭 IP 변경으로 인한 배포 파이프라인 실패

### 문제

- EC2 강제 중지 후 재시작 시 퍼블릭 IP가 변경됨
- GitHub Actions 배포 단계에서 SCP 에러:

```
drone-scp error: error copy file to dest: ***, error message: dial tcp ***:22: i/o timeout
```

### 원인

GitHub Secrets에 저장된 `EC2_HOST` 값이 이전 IP로 고정되어 있어 변경된 IP로 접근 시도 자체가 실패.

### 해결

GitHub 저장소 → Settings → Secrets and variables → Actions → `EC2_HOST` 값을 새 IP로 갱신.

### 재발 방지

- **Elastic IP** 할당으로 EC2 재기동과 무관하게 고정 IP 유지 → `EC2_HOST` 갱신 작업 자체를 제거 가능
- Route 53 A 레코드는 ALB를 가리키므로 이번 이슈와 무관 (확인만 수행)

</details>
