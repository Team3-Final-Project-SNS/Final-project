#!/bin/bash
# ============================================================
# Blue-Green 무중단 배포 스크립트
# ============================================================
# 동작 순서:
#   1. 현재 ALB가 트래픽을 보내는 쪽(blue/green)을 확인
#   2. 반대쪽(비활성) 환경에 새 코드로 컨테이너 빌드 & 실행
#   3. 새 컨테이너가 헬스체크(/actuator/health)에 통과할 때까지 대기
#   4. ALB의 3개 리스너 규칙(api, actuator, ws) 가중치를
#      비활성 환경 100%, 활성 환경 0% 로 전환
#   5. 이전 활성 환경 컨테이너 종료
# ============================================================

set -e  # 에러 발생 시 스크립트 즉시 중단 (중간에 실패하면 멈춰서 확인 가능하게)

# ---- 설정값: ALB 리스너 규칙 ARN, 대상 그룹 ARN ----
API_RULE_ARN="arn:aws:elasticloadbalancing:ap-northeast-2:024863981679:listener-rule/app/hankkipot-alb/c7db7cec80fb8558/6d4e3d1974782c39/6cb3dc7a7ab11c2b"
ACTUATOR_RULE_ARN="arn:aws:elasticloadbalancing:ap-northeast-2:024863981679:listener-rule/app/hankkipot-alb/c7db7cec80fb8558/6d4e3d1974782c39/f1da9833029d09c1"
WS_RULE_ARN="arn:aws:elasticloadbalancing:ap-northeast-2:024863981679:listener-rule/app/hankkipot-alb/c7db7cec80fb8558/6d4e3d1974782c39/71af14201fc45c27"

BLUE_TG_ARN="arn:aws:elasticloadbalancing:ap-northeast-2:024863981679:targetgroup/hankkipot-tg/2bc276d9acdb6f77"
GREEN_TG_ARN="arn:aws:elasticloadbalancing:ap-northeast-2:024863981679:targetgroup/hankkipot-tg-green/609b43e93a7af24e"

COMPOSE_FILE="docker-compose.prod.yml"
HEALTH_URL_BLUE="http://localhost:8080/actuator/health"
HEALTH_URL_GREEN="http://localhost:8081/actuator/health"

cd /home/ec2-user/app

# ---- 1. 현재 활성 환경(blue/green) 판단 ----
# describe-rules로 api-rule의 현재 가중치를 조회해서
# hankkipot-tg(blue)의 Weight가 100이면 현재는 blue가 활성 상태
echo "[1/5] 현재 활성 환경 확인 중..."

CURRENT_BLUE_WEIGHT=$(aws elbv2 describe-rules \
  --rule-arns "$API_RULE_ARN" \
  --query "Rules[0].Actions[0].ForwardConfig.TargetGroups[?TargetGroupArn=='$BLUE_TG_ARN'].Weight" \
  --output text)

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

echo "    현재 활성 환경: $ACTIVE"
echo "    새로 배포할 환경: $TARGET"

# ---- 2. 비활성 환경에 새 코드 빌드 & 실행 ----
# --profile green 옵션은 docker-compose.prod.yml에서
# app-green 서비스에 설정한 profiles: ["green"] 때문에 필요함
# (green일 때만 명시적으로 --profile green 추가)
echo "[2/5] $TARGET 환경 빌드 및 기동 중..."

if [ "$TARGET" == "green" ]; then
  docker-compose -f "$COMPOSE_FILE" --profile green up -d --build app-green
else
  docker-compose -f "$COMPOSE_FILE" up -d --build app-blue
fi

# ---- 3. 헬스체크 대기 ----
# 최대 60초(2초 간격 30회) 동안 /actuator/health가 "UP"을 반환할 때까지 대기
echo "[3/5] $TARGET 환경 헬스체크 대기 중..."

for i in $(seq 1 30); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$HEALTH_URL" || echo "000")
  if [ "$STATUS" == "200" ]; then
    echo "    헬스체크 통과 (시도 $i회)"
    break
  fi
  echo "    대기 중... (시도 $i/30, 상태코드: $STATUS)"
  sleep 2

  if [ "$i" == "30" ]; then
    echo "    헬스체크 실패! 배포를 중단합니다."
    exit 1
  fi
done

# ---- 4. ALB 가중치 전환 ----
# 3개 규칙(api, actuator, ws) 모두 동일하게
# 새 환경(target) 100%, 기존 환경(active) 0% 로 변경
echo "[4/5] ALB 트래픽을 $TARGET 환경으로 전환 중..."

for RULE_ARN in "$API_RULE_ARN" "$ACTUATOR_RULE_ARN" "$WS_RULE_ARN"; do
  aws elbv2 modify-rule \
    --rule-arn "$RULE_ARN" \
    --actions "Type=forward,ForwardConfig={TargetGroups=[{TargetGroupArn=$TARGET_TG_ARN,Weight=100},{TargetGroupArn=$INACTIVE_TG_ARN,Weight=0}]}"
done

echo "    전환 완료: $TARGET 100% / $ACTIVE 0%"

# 전환 직후 ALB의 connection draining(기존 연결 정리) 시간을 위해 잠시 대기
sleep 5

# ---- 5. 이전 환경 컨테이너 종료 ----
echo "[5/5] 이전 환경($ACTIVE) 컨테이너 종료 중..."

if [ "$ACTIVE" == "green" ]; then
  docker-compose -f "$COMPOSE_FILE" --profile green stop app-green
else
  docker-compose -f "$COMPOSE_FILE" stop app-blue
fi

echo ""
echo "============================================"
echo " 배포 완료! 현재 활성 환경: $TARGET"
echo "============================================"