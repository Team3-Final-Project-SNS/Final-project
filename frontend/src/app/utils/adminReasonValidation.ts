const MEANINGFUL_REASON_PATTERN = /[가-힣A-Za-z0-9]/;
const KOREAN_JAMO_ONLY_PATTERN = /^[ㄱ-ㅎㅏ-ㅣ\s]+$/;

export const getAdminReasonValidationMessage = (
  value: string,
  requiredMessage: string,
  invalidMessage: string = '잘못된 사유입니다.',
) => {
  const trimmedValue = value.trim();

  if (!trimmedValue) {
    return requiredMessage;
  }

  if (!MEANINGFUL_REASON_PATTERN.test(trimmedValue) || KOREAN_JAMO_ONLY_PATTERN.test(trimmedValue)) {
    return invalidMessage;
  }

  return null;
};
