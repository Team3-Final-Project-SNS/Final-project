# 배포 고도화

한끼팟 배포 구조의 현재 한계와 HTTPS, 무중단 배포 중심의 개선 방향을 정리한 문서입니다.

## 1. 현재 배포 구조 개요

## 아키텍처

현재 한끼팟 서비스는 단일 AWS EC2 인스턴스(t4g.medium) 위에 Docker Compose로 모든 서비스를 실행하는 구조입니다.

```text
[사용자] -> HTTP -> [EC2 t4g.medium]
                      ├── Spring Boot (8080)
                      ├── React/Vite (5173)
                      ├── MySQL (3307)
                      ├── Redis (6379)
                      ├── Kafka (9092)
                      ├── PostgreSQL/pgvector (5432)
                      ├── Prometheus (9090)
                      └── Grafana (3000)
```

## CI/CD 구조

- GitHub Actions `workflow_dispatch` (수동 트리거)
- 코드 전송 -> EC2에서 `docker-compose up --build -d`

---

## 2. 현재 배포 구조의 한계

## 2-1. 단일 장애점 (SPOF)

EC2 인스턴스 하나에 모든 서비스가 올라가 있어, EC2가 죽으면 전체 서비스가 중단됩니다.

| 항목 | 현재 상태 |
| --- | --- |
| 가용성 | 단일 인스턴스 -> 장애 시 100% 중단 |
| 복구 시간 | EC2 재시작 + Docker Compose 재실행 ≈ 5~10분 |
| 데이터 안정성 | MySQL만 RDS로 분리, 나머지는 컨테이너 볼륨 의존 |

## 2-2. 배포 시 서비스 중단 (다운타임)

`docker-compose down -> up --build` 방식으로 배포하면 빌드 시간(약 3~5분) 동안 서비스가 완전히 중단됩니다.

| 항목 | 현재 상태 |
| --- | --- |
| 배포 방식 | 중단 후 재시작 (Blue-Green/Rolling 없음) |
| 배포 소요 시간 | 최초 빌드 약 10~15분, 이후 약 3~5분 |
| 배포 중 가용성 | 0% (완전 중단) |

## 2-3. HTTPS 미적용

HTTP만 사용 중이어서 브라우저 보안 정책상 다음 기능들이 제한됩니다.

- `crypto.randomUUID()` 사용 불가
- Geolocation API (GPS 위치 인증) 사용 불가
- 카메라/마이크 접근 불가
- WebSocket 보안 연결 (WSS) 불가

## 2-4. 확장성 부재

트래픽이 증가해도 스케일 아웃이 불가능한 구조입니다. EC2 한 대에 모든 부하가 집중됩니다.

## 2-5. 수동 파일 관리

`.env`, `application-prod.yml`, `frontend/.env.local` 파일을 배포 담당자가 직접 scp로 EC2에 올려야 하는 구조로, 자동화가 안 되어 있습니다.

---

## 3. 개선 방향

## 선택한 개선: HTTPS 도입 + 무중단 배포

## 근거

1. **HTTPS 도입이 가장 시급**: GPS 위치 인증이 한끼팟의 핵심 기능인데, 현재 HTTP 환경에서는 동작하지 않아 E2E 테스트 자체가 불가능합니다.
2. **무중단 배포**: 팀원들이 기능을 개발하고 배포할 때마다 서비스가 수 분간 중단되는 것은 E2E 테스트와 실제 운영에 부적합합니다.

---

## 4. 개선 전/후 비교

## 4-1. HTTPS 도입

### 개선 전

| 항목 | 상태 |
| --- | --- |
| 프로토콜 | HTTP |
| GPS 위치 인증 | 불가 |
| crypto.randomUUID() | 불가 |
| WebSocket | WS (비암호화) |
| 데이터 전송 보안 | 평문 전송 |

### 개선 후 (도메인 + ACM + ALB)

| 항목 | 상태 |
| --- | --- |
| 프로토콜 | HTTPS |
| GPS 위치 인증 | 가능 |
| crypto.randomUUID() | 가능 |
| WebSocket | WSS (암호화) |
| 데이터 전송 보안 | TLS 암호화 |

### 구성 방법

```text
[사용자]
   ↓ HTTPS
[Route53 도메인]
   ↓
[ACM 인증서 (무료 SSL)]
   ↓
[ALB (Application Load Balancer)]
   ↓ HTTP
[EC2 t4g.medium]
```

## 4-2. 무중단 배포

### 개선 전

| 항목 | 상태 |
| --- | --- |
| 배포 방식 | docker-compose down -> up --build |
| 배포 중 다운타임 | 약 3~10분 |
| 자동화 | GitHub Actions 수동 트리거 |
| 롤백 | 수동 (이전 이미지 없음) |

### 개선 후 (Blue-Green 배포)

| 항목 | 상태 |
| --- | --- |
| 배포 방식 | Blue-Green (2개 환경 전환) |
| 배포 중 다운타임 | 0초 |
| 자동화 | GitHub Actions 자동화 |
| 롤백 | 즉시 (이전 환경으로 전환) |

### Blue-Green 배포 구조

```text
Blue 환경 (현재 운영)
   ↑ 트래픽
[Nginx Reverse Proxy / ALB]
   ↓ 배포 완료 후 전환
Green 환경 (새 버전 준비)
```

---

## 5. 장애 복구 시간 비교

| 시나리오 | 개선 전 | 개선 후 |
| --- | --- | --- |
| 일반 배포 | 3~10분 다운타임 | 0초 |
| 컨테이너 장애 | docker restart ≈ 1~2분 | 자동 헬스체크 + 재시작 |
| EC2 장애 | EC2 재부팅 ≈ 5~10분 | ALB + Auto Scaling으로 자동 복구 |
| 잘못된 배포 | 수동 롤백 ≈ 10분+ | Blue-Green 전환 ≈ 30초 |

---

## 6. 개선 작업 로드맵

- [ ] 도메인 구매 (Route53 또는 외부)
- [ ] Route53 호스팅 영역 생성
- [ ] ACM SSL 인증서 발급
- [ ] ALB 생성 및 HTTPS 리스너 설정
- [ ] EC2 보안그룹 수정 (ALB에서만 트래픽 허용)
- [ ] Blue-Green 배포 스크립트 작성
- [ ] GitHub Actions deploy.yml 업데이트
- [ ] 개선 전/후 성능 및 가용성 검증
