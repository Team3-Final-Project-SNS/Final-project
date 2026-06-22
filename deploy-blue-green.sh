# ---- 1. 현재 활성 환경(blue/green) 판단 ----
echo "[1/5] 현재 활성 환경 확인 중..."

CURRENT_BLUE_WEIGHT=$(aws elbv2 describe-rules \
  --rule-arns "$API_RULE_ARN" \
  --query "Rules[0].Actions[0].ForwardConfig.TargetGroups[?TargetGroupArn=='$BLUE_TG_ARN'].Weight" \
  --output text)

# 최초 배포 시 CURRENT_BLUE_WEIGHT가 "None" 또는 빈 값일 수 있음
# 이 경우 blue를 기본 활성으로 간주하고 green으로 배포
if [ "$CURRENT_BLUE_WEIGHT" == "100" ]; then
  ACTIVE="blue"
  TARGET="green"
  TARGET_TG_ARN="$GREEN_TG_ARN"
  INACTIVE_TG_ARN="$BLUE_TG_ARN"
  HEALTH_URL="$HEALTH_URL_GREEN"
else
  # green이 활성이거나 최초 배포 시 → blue로 배포
  ACTIVE="green"
  TARGET="blue"
  TARGET_TG_ARN="$BLUE_TG_ARN"
  INACTIVE_TG_ARN="$GREEN_TG_ARN"
  HEALTH_URL="$HEALTH_URL_BLUE"
fi