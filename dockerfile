# ──────────────────────────────────────────────────────────────
# [1단계] 빌드 스테이지
# JDK + Gradle로 소스코드를 JAR 파일로 빌드
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 의존성 파일 먼저 복사 → 소스코드 안 바뀌면 이 레이어 캐시 재사용
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 실행 권한 부여
RUN chmod +x gradlew

# 의존성 미리 다운로드 (캐시 레이어 분리)
RUN ./gradlew dependencies --no-daemon

# 소스코드 복사
COPY src src

# JAR 빌드 (테스트 제외 — CI에서 따로 돌림)
RUN ./gradlew bootJar -x test --no-daemon

# ──────────────────────────────────────────────────────────────
# [2단계] 실행 스테이지
# JRE만 있는 가벼운 이미지에 JAR만 복사해서 실행
# ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드 스테이지에서 만든 JAR만 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 보안: root 대신 전용 유저로 실행
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8080

# 난수 생성 최적화 옵션 → 앱 초기화 속도 향상
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]