#!/bin/bash
# Blue-Green 배포 스크립트
# 실행 흐름: 활성 환경 판단 → 비활성 컨테이너 빌드 → 헬스체크 → 트래픽 전환 → 이전 컨테이너 중지
set -e  # 명령어 실패 시 즉시 중단

# ---- 1. 현재 활성 환경(blue/green) 판단 ----
echo "[1/5] 현재 활성 환경 확인 중..."

# ALB 규칙에서 현재 blue 타겟 그룹의 트래픽 가중치 조회
CURRENT_BLUE_WEIGHT=$(aws elbv2 describe-rules \
  --rule-arns "$API_RULE_ARN" \
  --query "Rules[0].Actions[0].ForwardConfig.TargetGroups[?TargetGroupArn=='$BLUE_TG_ARN'].Weight" \
  --output text)

# blue 가중치가 100이면 blue가 활성 → green에 배포
# 그 외(green 활성 또는 최초 배포)면 blue에 배포
if [ "$CURRENT_BLUE_WEIGHT" == "100" ]; then
  ACTIVE="blue"
  TARGET="green"
  TARGET_TG_ARN="$GREEN_TG_ARN"
  INACTIVE_TG_ARN="$BLUE_TG_ARN"
  HEALTH_URL="$HEALTH_URL_GREEN"
else
  ACTIVE="green"
  TARGET="blue"
  TARGET_TG_ARN="$BLUE_TG_ARN"
  INACTIVE_TG_ARN="$GREEN_TG_ARN"
  HEALTH_URL="$HEALTH_URL_BLUE"
fi

echo "현재 활성: $ACTIVE → 배포 대상: $TARGET"

# ---- 2. 비활성 컨테이너에 새 이미지 빌드 & 실행 ----
echo "[2/5] $TARGET 컨테이너 빌드 및 실행 중..."

# green은 docker-compose.prod.yml에서 profiles: ["green"]으로 분리되어 있음
# blue는 기본 서비스라 --profile 없이 실행
if [ "$TARGET" == "green" ]; then
  docker-compose -f docker-compose.prod.yml \
    --profile green \
    up --build -d app-green
else
  docker-compose -f docker-compose.prod.yml \
    up --build -d app-blue
fi

# ---- 3. 헬스체크 (최대 120초 대기) ----
echo "[3/5] $TARGET 헬스체크 중... (최대 120초)"

RETRY=0
MAX_RETRY=24  # 5초 간격 × 24회 = 120초

# /actuator/health 가 200 응답할 때까지 반복
until curl -sf "$HEALTH_URL" > /dev/null; do
  RETRY=$((RETRY + 1))

  # 최대 재시도 초과 시 롤백
  if [ $RETRY -ge $MAX_RETRY ]; then
    echo "ERROR: 헬스체크 실패 - $TARGET 컨테이너 중지 후 종료"
    docker-compose -f docker-compose.prod.yml stop app-$TARGET
    exit 1
  fi

  echo "대기 중... ($RETRY/$MAX_RETRY)"
  sleep 5
done

echo "$TARGET 헬스체크 통과!"

# ---- 4. ALB 트래픽 전환 ----
echo "[4/5] ALB 트래픽을 $ACTIVE → $TARGET 으로 전환 중..."

# 새 타겟 그룹(TARGET)에 100%, 이전 타겟 그룹(INACTIVE)에 0% 설정
aws elbv2 modify-rule \
  --rule-arn "$API_RULE_ARN" \
  --actions "[{
    \"Type\": \"forward\",
    \"ForwardConfig\": {
      \"TargetGroups\": [
        {\"TargetGroupArn\": \"$TARGET_TG_ARN\", \"Weight\": 100},
        {\"TargetGroupArn\": \"$INACTIVE_TG_ARN\", \"Weight\": 0}
      ]
    }
  }]"

echo "트래픽 전환 완료: $ACTIVE → $TARGET"

# ---- 5. 이전 활성 컨테이너 중지 ----
echo "[5/5] 이전 컨테이너($ACTIVE) 중지 중..."

# 트래픽이 완전히 전환된 후 이전 컨테이너 중지
docker-compose -f docker-compose.prod.yml stop app-$ACTIVE

echo "=============================="
echo "배포 완료! 현재 활성: $TARGET"
echo "=============================="