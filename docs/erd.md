# ERD

한끼팟 최종 ERD DDL 문서입니다.

## 테이블 목록

총 27개 테이블

| No | Table |
| ---: | --- |
| 1 | `review_bad_tags` |
| 2 | `user_locations` |
| 3 | `user_avoid_relations` |
| 4 | `matches` |
| 5 | `inquiries` |
| 6 | `ai_support_chat_messages` |
| 7 | `ai_prompt_templates` |
| 8 | `chat_members` |
| 9 | `admins` |
| 10 | `payments` |
| 11 | `ai_call_metrics` |
| 12 | `review_good_tags` |
| 13 | `chat_rooms` |
| 14 | `reviews` |
| 15 | `universities` |
| 16 | `ai_conversation_sessions` |
| 17 | `notifications` |
| 18 | `reports` |
| 19 | `meet_verifications` |
| 20 | `point_transactions` |
| 21 | `term_agreements` |
| 22 | `posts` |
| 23 | `ai_report_summaries` |
| 24 | `disputes` |
| 25 | `users` |
| 26 | `inquiries_answers` |
| 27 | `chat_messages` |

## DDL

```sql
CREATE TABLE `review_bad_tags` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`후기 ID`	bigint	NOT NULL	COMMENT 'ID',
	`아쉬워요 태그`	ENUM( 'LATE', 'NO_REPLY', 'UNCOMFORTABLE', 'BAD_MANNER', 'REPORT_NEEDED' )	NOT NULL	COMMENT '아쉬워요 태그',
	`점수 변화량.`	int	NOT NULL,
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `user_locations` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`매칭 ID`	bigint	NOT NULL	COMMENT '매칭 ID',
	`유저 ID`	bigint	NOT NULL	COMMENT '유저 ID',
	`위도`	decimal(10, 7)	NOT NULL	COMMENT '위도',
	`경도`	decimal(10, 7)	NOT NULL	COMMENT '경도',
	`업데이트 시각`	timestamp	NOT NULL	COMMENT '마지막 위치 업데이트 시각',
	`사용자가 반경 안에 있었는지 여부`	boolean	NOT NULL,
	`마지막으로 약속 장소 반경 안에 있었던 시각`	timestamp	NOT NULL,
	`Field`	timestamp	NOT NULL
);

CREATE TABLE `user_avoid_relations` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`유저 ID`	bigint	NOT NULL	COMMENT 'ID',
	`회피 대상 유저 ID`	bigint	NOT NULL	COMMENT 'ID',
	`후기 ID`	bigint	NOT NULL	COMMENT 'ID',
	`생성일`	timestamp	NOT NULL	COMMENT '블라인드 처리를 발생시킨 매칭 ID'
);

CREATE TABLE `matches` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`게시글 ID`	bigint	NOT NULL	COMMENT '게시글 ID',
	`신청자 ID`	bigint	NOT NULL	COMMENT '신청자 ID',
	`신청자 예치 포인트`	int	NOT NULL	COMMENT '신청자 예치 포인트',
	`매칭 상태`	ENUM('MATCHED', 'COMPLETED', 'CANCELLED', 'AUTHOR_NO_SHOW', 'APPLICANT_NO_SHOW', 'BOTH_NO_SHOW', 'DISPUTE')	NOT NULL	DEFAULT 'MATCHED'	COMMENT '매칭 상태',
	`매칭 시각`	timestamp	NOT NULL	COMMENT '매칭 확정 시각',
	`매칭 완료 시각`	timestamp	NULL	COMMENT '만남 완료 시각'
);

CREATE TABLE `inquiries` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`유저 ID`	bigint	NOT NULL	COMMENT '문의 작성 유저 ID',
	`문의 제목`	varchar(200)	NOT NULL	COMMENT '문의 제목',
	`문의 내용`	text	NOT NULL	COMMENT '문의 내용',
	`문의 유형`	ENUM('ACCOUNT', 'PAYMENT', 'USAGE', 'HISTORY' 'MATCH', 'REPORT', 'OTHER')	NOT NULL	COMMENT '문의 유형',
	`답변 처리 상태`	ENUM('PENDING', 'READ', 'ANSWERED', 'WITHDRAWN')	NOT NULL	DEFAULT 'PENDING'	COMMENT '답변 처리 상태',
	`생성일`	timestamp	NOT NULL	DEFAULT DEFAULT CURRENT_TIMESTAMP	COMMENT '생성일',
	`취소일`	timestamp	NULL
);

CREATE TABLE `ai_support_chat_messages` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`유저 ID`	bigint	NOT NULL	COMMENT '유저 ID',
	`대화 세션 ID`	varchar(100)	NOT NULL	COMMENT '대화 세션 ID',
	`AI 요청 추적 ID`	varchar(64)	NOT NULL	COMMENT 'AI 요청 추적 ID',
	`메시지 작성 주체`	ENUM('USER', 'ASSISTANT')	NOT NULL	COMMENT '메시지 작성 주체',
	`메시지 내용`	text	NOT NULL	COMMENT '메시지 내용',
	`문의 카테고리`	ENUM('MATCH', 'POST', 'POINT', 'CHAT', 'REPORT', 'ACCOUNT', 'MEET', 'GENERAL')	NULL	COMMENT '문의 카테고리',
	`AI 응답 요약`	varchar(500)	NULL	COMMENT 'AI 응답 요약',
	`추가 조치 필요 여부`	boolean	NULL	COMMENT '추가 조치 필요 여부',
	`fallback 사용 여부`	boolean	NULL	COMMENT 'fallback 사용 여부',
	`사용 모델명`	varchar(80)	NULL	COMMENT '사용 모델명',
	`프롬프트 템플릿 ID`	bigint	NULL	COMMENT '프롬프트 템플릿 ID',
	`프롬프트 버전`	varchar(30)	NULL	COMMENT '프롬프트 버전',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `ai_prompt_templates` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`프롬프트 용도`	ENUM('MATCHING_CHAT', 'SUPPORT_CHAT', 'REPORT_SUMMARY')	NOT NULL	COMMENT '프롬프트 용도',
	`AI 기능 구분`	ENUM('MATCHING', 'SUPPORT', 'REPORT')	NOT NULL	COMMENT 'AI 기능 구분',
	`프롬프트 버전`	varchar(30)	NOT NULL	COMMENT '프롬프트 버전',
	`프롬프트 파일명`	varchar(255)	NOT NULL	COMMENT '프롬프트 파일명',
	`활성 여부`	boolean	NOT NULL	COMMENT '활성 여부',
	`프롬프트 설명`	varchar(500)	NULL	COMMENT '프롬프트 설명',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `chat_members` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`채팅방 ID`	bigint	NOT NULL	COMMENT '채팅방 ID',
	`유저 ID`	bigint	NOT NULL	COMMENT '참여자 유저 ID',
	`역할`	ENUM('HOST', 'GUEST')	NOT NULL	COMMENT '역할 (HOST: 등록자, GUEST: 신청자)',
	`멤버 상태`	ENUM('ACTIVE', 'NO_SHOW', 'LEFT')	NOT NULL,
	`입장 시각`	timestamp	NOT NULL	COMMENT '참여 시각',
	`노쇼 판정 시각`	timestamp	NULL
);

CREATE TABLE `admins` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`관리자 이메일`	varchar(255)	NOT NULL	COMMENT '관리자 이메일',
	`관리자 비밀번호`	varchar(255)	NOT NULL	COMMENT '비밀번호 (암호화)',
	`관리자 이름`	varchar(50)	NOT NULL	COMMENT '관리자 이름',
	`관리자 권한 등급`	ENUM('SUPER_ADMIN')	NOT NULL	DEFAULT 'SUPER_ADMIN'	COMMENT '관리자 권한 등급',
	`계정 활성화 여부`	boolean	NOT NULL	DEFAULT true	COMMENT '계정 활성 여부',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일',
	`수정일`	timestamp	NULL	COMMENT '수정일'
);

CREATE TABLE `payments` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`결제 유저 ID`	bigint	NOT NULL	COMMENT '결제 유저 ID',
	`고유 주문  ID`	varchar(100)	NOT NULL	COMMENT 'PortOne 고유 주문 ID',
	`충전패키지`	ENUM('P_3000', 'P_5000', 'P_10000', 'P_20000')	NOT NULL,
	`충전될 포인트`	int	NOT NULL	COMMENT '충전될 포인트',
	`실제 결제 금액`	int	NOT NULL	COMMENT '실제 결제 금액 (원)',
	`결제 수단`	varchar(50)	NULL	COMMENT '결제 수단 (card, kakaopay 등)',
	`결제 상태`	ENUM('READY', 'PAID', 'CANCELLED', 'FAILED')	NOT NULL	DEFAULT 'READY'	COMMENT '결제 상태',
	`취소 사유`	varchar(255)	NULL,
	`실패사유`	varchar(255)	NULL,
	`결제완료시각`	timestamp	NULL	COMMENT '결제 요청 시각',
	`결제취소시각`	timestamp	NULL	COMMENT '결제 완료·실패 확정 시각'
);

CREATE TABLE `ai_call_metrics` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`AI 요청 추적 ID`	varchar(64)	NOT NULL	COMMENT 'AI 요청 추적 ID',
	`유저 ID`	bigint	NULL	COMMENT '유저 ID',
	`AI 기능 구분`	ENUM('MATCHING', 'SUPPORT', 'REPORT')	NOT NULL	COMMENT 'AI 기능 구분',
	`사용 모델명`	varchar(80)	NOT NULL	COMMENT '사용 모델명',
	`입력 토큰 수`	int	NULL	COMMENT '입력 토큰 수',
	`응답 토큰 수`	int	NULL	COMMENT '응답 토큰 수',
	`프롬프트 템플릿 ID`	bigint	NULL	COMMENT '프롬프트 템플릿 ID',
	`프롬프트 버전`	varchar(30)	NULL	COMMENT '프롬프트 버전',
	`전체 토큰 수`	int	NULL	COMMENT '전체 토큰 수',
	`응답 지연 시간`	bigint	NULL	COMMENT '응답 지연 시간',
	`호출 처리 상태`	ENUM('SUCCESS', 'FAILED', 'FALLBACK')	NOT NULL	COMMENT '호출 처리 상태',
	`오류 유형`	ENUM( 'TIMEOUT', 'RATE_LIMIT', 'SERVER_ERROR', 'INVALID_API_KEY', 'INVALID_RESPONSE', 'SCHEMA_VALIDATION_FAILED', 'TOOL_ERROR', 'TOOL_TIMEOUT', 'TOOL_NOT_FOUND', 'PROMPT_LOAD_ERROR', 'PROMPT_NOT_FOUND', 'CONTENT_FILTERED', 'FALLBACK_FAILED', 'UNKNOWN' )	NULL	COMMENT '오류 유형',
	`오류 메시지`	varchar(500)	NULL	COMMENT '오류 메시지',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `review_good_tags` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`후기 ID`	bigint	NOT NULL	COMMENT 'ID',
	`좋아요 태그`	ENUM( 'ON_TIME', 'KIND', 'GOOD_COMMUNICATION', 'CLEAN_MANNER', 'WANT_MEET_AGAIN' )	NOT NULL	COMMENT '좋아요 태그',
	`점수 변화량`	int	NOT NULL,
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `chat_rooms` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`게시글 ID`	bigint	NOT NULL,
	`채팅방 유형`	ENUM('ONE_TO_ONE', 'GROUP')	NOT NULL	DEFAULT 'ONE_TO_ONE'	COMMENT '채팅방 유형',
	`채팅방 상태`	ENUM('ACTIVE', 'READ_ONLY', 'DEACTIVATED')	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '활성 여부',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일',
	`비활성화 시각`	timestamp	NULL	COMMENT '비활성화 시각'
);

CREATE TABLE `reviews` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`매칭 ID`	bigint	NOT NULL	COMMENT '매칭 ID',
	`후기 작성자 ID`	bigint	NOT NULL	COMMENT '후기 작성자 ID',
	`태그 점수 변화량`	int	NOT NULL	COMMENT '매너 점수 (1~5점)',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `universities` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`학교명`	varchar(100)	NOT NULL	COMMENT '학교명',
	`이메일 도메인`	varchar(100)	NOT NULL	COMMENT '이메일 도메인',
	`활성 여부`	boolean	NOT NULL	COMMENT '활성 여부',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일',
	`비활성화일`	timestamp	NULL	COMMENT '비활성화일'
);

CREATE TABLE `ai_conversation_sessions` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`유저 ID`	bigint	NOT NULL	COMMENT '유저 ID',
	`AI 기능 구분`	ENUM('MATCHING', 'SUPPORT')	NOT NULL	COMMENT 'AI 기능 구분',
	`대화 세션 ID`	varchar(100)	NOT NULL	COMMENT '대화 세션 ID',
	`마지막 대화 시각`	timestamp	NULL	COMMENT '마지막 대화 시각',
	`세션 만료 시각`	timestamp	NULL	COMMENT '세션 만료 시각',
	`활성 여부`	boolean	NOT NULL	COMMENT '활성 여부',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `notifications` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`수신 유저 ID`	bigint	NOT NULL	COMMENT '수신 유저 ID',
	`수신자 유형`	ENUM('USER','ADMIN')	NOT NULL,
	`알림 유형 1`	ENUM('MATCH_APPLIED', 'MATCH_CONFIRMED', 'MATCH_CANCELLED', 'MEET_REMINDER', 'MEET_IMMINENT', 'MEET_OVERDUE', 'MEET_COMPLETED', 'REVIEW_DEADLINE_REMINDER', 'REVIEW_REWARD', 'MANNER_TEMPERATURE_CHANGED', 'CHAT_RECEIVED', 'PLACE_VERIFIED', 'CHAT_MEMBER_LEFT', 'NO_SHOW_WARNING', 'NO_SHOW_CONFIRMED', 'MEET_EXTEND_REQUESTED', 'MEET_EXTEND_ACCEPTED', 'MEET_EXTEND_REJECTED', 'MEET_EXTEND_EXPIRED')	NOT NULL	COMMENT '알림 유형',
	`알림유형 2`	ENUM('DISPUTE_SUBMITTED', 'DISPUTE_RESULT', 'DISPUTE_PENDING', 'DISPUTE_DEADLINE_REMINDER', 'REPORT_SUBMITTED', 'REPORT_REWARD', 'REPORT_REJECTED', 'PAYMENT_SUCCESS', 'PAYMENT_FAILED', 'PAYMENT_CANCEL_SUCCESS', 'PAYMENT_CANCEL_FAILED', 'INQUIRY_SUBMITTED', 'INQUIRY_ANSWERED', 'ACCOUNT_SUSPENDED', 'ACCOUNT_UNSUSPENDED', 'POST_WARNED_1', 'POST_WARNED_2', 'POST_EXPIRING_SOON', 'POST_EXPIRED', 'POST_DELETED', 'POST_RESTORED')	NULL,
	`알림 제목`	varchar(100)	NOT NULL	COMMENT '알림 제목',
	`알림 내용`	varchar(500)	NOT NULL	COMMENT '알림 내용',
	`연관 도메인`	ENUM('MATCH', 'MEET', 'CHAT', 'POINT', 'REPORT', 'DISPUTE', 'INQUIRY', 'ACCOUNT', 'POST', 'SYSTEM')	NOT NULL	COMMENT '연관 도메인',
	`관련 도메인 ID`	bigint	NULL	COMMENT '관련 엔티티 ID (클릭 시 화면 이동용)',
	`읽음 여부`	boolean	NOT NULL	DEFAULT false	COMMENT '읽음 여부',
	`읽은 시각`	timestamp	NULL	COMMENT '읽은 시각',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `reports` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`신고한 유저 ID`	bigint	NOT NULL	COMMENT '신고한 유저 ID',
	`신고 대상 ID`	bigint	NOT NULL	COMMENT '신고 대상 엔티티 ID',
	`신고 사유`	ENUM('SPAM', 'OBSCENE', 'FRAUD', 'ABUSE', 'OTHER')	NOT NULL	COMMENT '신고 사유',
	`신고 상세 내용`	varchar(500)	NULL	COMMENT '신고 상세 내용',
	`관리자 처리 상태`	ENUM('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')	NOT NULL	DEFAULT 'PENDING'	COMMENT '관리자 처리 상태',
	`신고 채택 포상 지급 여부`	boolean	NOT NULL	DEFAULT false	COMMENT '신고 채택 포상 50P 지급 여부',
	`처리한 관리자 ID`	bigint	NULL	COMMENT '처리한 관리자 ID',
	`신고 접수 시각`	timestamp	NOT NULL	COMMENT '신고 접수 시각',
	`처리 완료 시각`	timestamp	NULL	COMMENT '처리 완료 시각',
	`신고 취소 시각`	timestamp	NULL
);

CREATE TABLE `meet_verifications` (
	`id`	bigint	NOT NULL,
	`매칭 ID`	bigint	NOT NULL,
	`등록자 GPS 50m 진입 시각`	timestamp	NULL,
	`신청자 GPS 50m 진입 시각`	timestamp	NULL,
	`QR 토큰`	varchar(255)	NULL,
	`만남 인증 여부`	boolean	NULL,
	`인증 상태`	ENUM('PENDING', 'VERIFIED', 'DONE', 'HOST_NO_SHOW', 'GUEST_NO_SHOW', 'BOTH_NO_SHOW', 'DISPUTE', 'NO_SHOW_CONFIRMED','NO_SHOW_CANCELLED')	NOT NULL	DEFAULT 'PENDING'	COMMENT ''PENDING'',
	`이의제기 전 원래 노쇼 상태 기억용`	VARCHAR(20)	NULL,
	`QR 만료 시각`	timestamp	NULL,
	`인증 완료 시각`	timestamp	NULL,
	`만남 시간 10분 연장 사용 여부 (1회 한정)`	boolean	NOT NULL	DEFAULT false,
	`연장 적용 시각`	timestamp	NULL,
	`연장 요청 처리 상태`	ENUM('NONE', 'REQUESTED', 'ACCEPTED', 'REJECTED', 'EXPIRED')	NULL	DEFAULT 'NONE',
	`연장 요청한 유저 ID`	bigint	NULL,
	`연장 요청 시각 (타임 아웃 계산용)`	timestamp	NULL,
	`노쇼 판정 시각`	timestamp	NULL,
	`30분 전 알림 발송 여부`	boolean	NOT NULL	DEFAULT false,
	`15분 전 알림 발송 여부`	boolean	NOT NULL	DEFAULT false,
	`만남 임박 알림 발송 여부`	boolean	NOT NULL	DEFAULT false,
	`노쇼 예정 알림 발송 시도 여부`	BOOLEAN	NOT NULL	DEFAULT false,
	`노쇼 확정 알림 발송 시도 여부`	BOOLEAN	NOT NULL	DEFAULT false
);

CREATE TABLE `point_transactions` (
	`id`	bigint	NOT NULL,
	`유저 ID`	bigint	NOT NULL,
	`매칭 ID`	bigint	NULL,
	`포인트 변동량`	int	NOT NULL,
	`거래 유형`	ENUM('JOIN_BONUS', 'CHARGE' , 'CHARGE_CANCELLED' , 'DEPOSIT' , 'EDIT_DEPOSIT')	NOT NULL,
	`거래유형`	ENUM('REFUND' , 'PARTIAL_REFUND' , 'PENALTY' , 'REPORT_REWARD' , 'REVIEW_REWARD')	NULL,
	`포인트 종류`	ENUM('FREE', 'PAID')	NOT NULL,
	`거래 후 잔액`	int	NOT NULL,
	`거래 설명`	varchar(255)	NULL,
	`생성일`	timestamp	NOT NULL
);

CREATE TABLE `term_agreements` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`유저 ID`	bigint	NOT NULL	COMMENT '유저 ID',
	`약관 버전`	varchar(20)	NOT NULL	COMMENT '약관 버전',
	`동의 시각`	timestamp	NOT NULL	COMMENT '동의 시각'
);

CREATE TABLE `posts` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`작성자 ID`	bigint	NOT NULL	COMMENT '작성자 ID',
	`만남 희망 시간`	timestamp	NOT NULL	COMMENT '만남 희망 시간',
	`만남 장소명`	varchar(200)	NOT NULL	COMMENT '만남 장소명',
	`약속 장소 위도`	decimal(10, 7)	NOT NULL	COMMENT '약속 장소 위도',
	`약속 장소 경도`	decimal(10, 7)	NOT NULL	COMMENT '약속 장소 경도',
	`한마디`	text	NULL	COMMENT '한마디',
	`책임비 포인트`	int	NOT NULL	COMMENT '책임비 포인트',
	`게시글 상태`	ENUM('OPEN', 'MATCHED', 'COMPLETED', 'CANCELLED', 'EXPIRED')	NOT NULL	DEFAULT 'OPEN'	COMMENT '게시글 상태',
	`최대 참여 인원`	int	NOT NULL,
	`현재 참여 인원`	int	NOT NULL,
	`삭제 사유`	varchar(500)	NULL,
	`버전(낙관락)`	bigint	NOT NULL,
	`생성일`	timestamp	NOT NULL	COMMENT '생성일',
	`수정일`	timestamp	NULL	COMMENT '수정일',
	`삭제 완료 시각`	timestamp	NULL
);

CREATE TABLE `ai_report_summaries` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`AI 요청 추적 ID`	varchar(64)	NOT NULL	COMMENT 'AI 요청 추적 ID',
	`대화 세션 ID`	varchar(100)	NOT NULL	COMMENT '신고 ID',
	`관리자 ID`	bigint	NULL	COMMENT '관리자 ID',
	`처리한 업무 영역`	EUNM('DASHBOARD', 'POST', 'REPORT', 'INQUIRY', 'DISPUTE', 'USER', 'PAYMENT', 'FAQ', 'GENERAL')	NOT NULL,
	`관리자 대상 타입`	varchar(30)	NOT NULL,
	`단건 대상이 있는 경우 대상 ID`	bigint	NULL,
	`관리자 요청 원문`	text	NOT NULL,
	`관리자 화면 AI 답변`	text	NOT NULL,
	`신고 요약`	varchar(500)	NOT NULL	COMMENT '신고 요약',
	`판단 근거`	varchar(1000)	NOT NULL	COMMENT '판단 근거',
	`처리 방향 제안 이유`	varchar(1000)	NULL	COMMENT '처리 방향 제안 이유',
	`신고 사유`	ENUM('SPAM', 'OBSCENE', 'FRAUD', 'ABUSE', 'OTHER')	NOT NULL	COMMENT '신고 사유',
	`처리 방향 제안`	ENUM('ACCEPT', 'REJECT', 'NEEDS_REVIEW')	NOT NULL	COMMENT '처리 방향 제안',
	`위험도`	ENUM('LOW', 'MEDIUM', 'HIGH')	NOT NULL	COMMENT '위험도',
	`AI 판단 신뢰도`	int	NULL	COMMENT 'AI 판단 신뢰도',
	`관리자 검토 필요 여부`	boolean	NOT NULL	COMMENT '관리자 검토 필요 여부',
	`답변 생성 데이터 출처`	varchar(30)	NULL,
	`답변 생성 사용된 툴`	text	NOT NULL,
	`rag 사용 여부`	boolean	NOT NULL,
	`rag에 대한 근거`	boolean	NULL,
	`fallback 사용 여부`	boolean	NOT NULL	COMMENT 'fallback 사용 여부',
	`사용 모델명`	varchar(80)	NOT NULL	COMMENT '사용 모델명',
	`프롬프트 템플릿 ID`	bigint	NULL	COMMENT '프롬프트 템플릿 ID',
	`프롬프트 버전`	varchar(30)	NULL	COMMENT '프롬프트 버전',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

CREATE TABLE `disputes` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`분쟁 대상 매칭 ID`	bigint	NOT NULL	COMMENT '분쟁 대상 매칭 ID',
	`이의제기 제출 유저 ID`	bigint	NOT NULL	COMMENT '이의제기 제출 유저 ID',
	`이의제기 사유 타입`	ENUM('FUNERAL_CEREMONY', 'MEDICAL_EMERGENCY', 'PHONE_MALFUNCTION', 'GPS_ERROR', 'QR_ERROR', 'ADMIN_OVERRIDE')	NOT NULL,
	`이의 제기 사유`	text	NOT NULL	COMMENT '이의제기 사유',
	`증빙 자료`	varchar(500)	NULL,
	`처리 상태`	ENUM('SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED', 'PARTIALLY_ACCEPTED', 'REJECTED', 'HOLD')	NOT NULL	DEFAULT 'SUBMITTED'	COMMENT '처리 상태',
	`관리자 ID`	bigint	NULL	COMMENT '검토 관리자 ID',
	`관리자 최종 판정 사유`	text	NULL	COMMENT '관리자 최종 판정 사유',
	`관리자 처리 완료 시각`	timestamp	NULL	COMMENT '관리자 처리 완료 시각',
	`보류 판정 시각`	timestamp	NULL,
	`재이의제기에서 원본 이의제기 ID`	bigint	NULL,
	`이의제기 제출 시각`	timestamp	NOT NULL	COMMENT '이의제기 제출 시각 (노쇼 판정 후 24시간 이내)'
);

CREATE TABLE `users` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`유저 이메일`	varchar(255)	NOT NULL	COMMENT '이메일',
	`유저 비밀번호`	varchar(255)	NOT NULL	COMMENT '비밀번호',
	`유저 이름`	varchar(50)	NOT NULL	COMMENT '이름',
	`유저 닉네임`	varchar(50)	NOT NULL	COMMENT '닉네임',
	`학교 ID`	bigint	NOT NULL	COMMENT '학교 ID',
	`학과`	varchar(100)	NOT NULL	COMMENT '학과',
	`학번`	varchar(20)	NOT NULL	COMMENT '학번',
	`생년월일`	date	NOT NULL	COMMENT '생년월일',
	`성별`	ENUM('MALE', 'FEMALE')	NOT NULL	COMMENT '성별',
	`무료포인트`	int	NOT NULL	DEFAULT 0	COMMENT '보유 포인트',
	`유료포인트`	int	NOT NULL	DEFAULT 0,
	`매너 온도`	decimal(4, 1)	NOT NULL	DEFAULT 36.5	COMMENT '매너 온도',
	`활성 상태`	ENUM('ACTIVE', 'SUSPENDED', 'WITHDRAWN')	NOT NULL	DEFAULT 'ACTIVE'	COMMENT '계정 상태',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일',
	`수정일`	timestamp	NULL	COMMENT '수정일',
	`회원탈퇴일`	timestamp	NULL,
	`신고 기능 박탈 만료 시각`	timestamp	NULL,
	`정지 만료일`	timestamp	NULL
);

CREATE TABLE `inquiries_answers` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`원 문의 ID`	bigint	NOT NULL	COMMENT '원 문의 ID',
	`답변 관리자 ID`	bigint	NOT NULL	COMMENT '답변 관리자 ID',
	`관리자 이름`	varchar(50)	NOT NULL,
	`답변 내용`	text	NOT NULL	COMMENT '답변 내용',
	`답변 작성 시각`	timestamp	NOT NULL	COMMENT '답변 작성 시각'
);

CREATE TABLE `chat_messages` (
	`id`	bigint	NOT NULL	COMMENT 'ID',
	`채팅방 ID`	bigint	NOT NULL	COMMENT '채팅방 ID',
	`발신자 ID`	bigint	NOT NULL	COMMENT '발신자 ID',
	`메시지 내용`	text	NOT NULL	COMMENT '메시지 내용 (AI 마스킹 적용 후 저장)',
	`읽음 여부`	boolean	NOT NULL	DEFAULT false	COMMENT '읽음 여부',
	`생성일`	timestamp	NOT NULL	COMMENT '생성일'
);

ALTER TABLE `review_bad_tags` ADD CONSTRAINT `PK_REVIEW_BAD_TAGS` PRIMARY KEY (
	`id`
);

ALTER TABLE `user_locations` ADD CONSTRAINT `PK_USER_LOCATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `user_avoid_relations` ADD CONSTRAINT `PK_USER_AVOID_RELATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `matches` ADD CONSTRAINT `PK_MATCHES` PRIMARY KEY (
	`id`
);

ALTER TABLE `inquiries` ADD CONSTRAINT `PK_INQUIRIES` PRIMARY KEY (
	`id`
);

ALTER TABLE `ai_support_chat_messages` ADD CONSTRAINT `PK_AI_SUPPORT_CHAT_MESSAGES` PRIMARY KEY (
	`id`
);

ALTER TABLE `ai_prompt_templates` ADD CONSTRAINT `PK_AI_PROMPT_TEMPLATES` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_members` ADD CONSTRAINT `PK_CHAT_MEMBERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `admins` ADD CONSTRAINT `PK_ADMINS` PRIMARY KEY (
	`id`
);

ALTER TABLE `payments` ADD CONSTRAINT `PK_PAYMENTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `ai_call_metrics` ADD CONSTRAINT `PK_AI_CALL_METRICS` PRIMARY KEY (
	`id`
);

ALTER TABLE `review_good_tags` ADD CONSTRAINT `PK_REVIEW_GOOD_TAGS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_rooms` ADD CONSTRAINT `PK_CHAT_ROOMS` PRIMARY KEY (
	`id`
);

ALTER TABLE `reviews` ADD CONSTRAINT `PK_REVIEWS` PRIMARY KEY (
	`id`
);

ALTER TABLE `universities` ADD CONSTRAINT `PK_UNIVERSITIES` PRIMARY KEY (
	`id`
);

ALTER TABLE `ai_conversation_sessions` ADD CONSTRAINT `PK_AI_CONVERSATION_SESSIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `notifications` ADD CONSTRAINT `PK_NOTIFICATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `reports` ADD CONSTRAINT `PK_REPORTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `meet_verifications` ADD CONSTRAINT `PK_MEET_VERIFICATIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `point_transactions` ADD CONSTRAINT `PK_POINT_TRANSACTIONS` PRIMARY KEY (
	`id`
);

ALTER TABLE `term_agreements` ADD CONSTRAINT `PK_TERM_AGREEMENTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `posts` ADD CONSTRAINT `PK_POSTS` PRIMARY KEY (
	`id`
);

ALTER TABLE `ai_report_summaries` ADD CONSTRAINT `PK_AI_REPORT_SUMMARIES` PRIMARY KEY (
	`id`
);

ALTER TABLE `disputes` ADD CONSTRAINT `PK_DISPUTES` PRIMARY KEY (
	`id`
);

ALTER TABLE `users` ADD CONSTRAINT `PK_USERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `inquiries_answers` ADD CONSTRAINT `PK_INQUIRIES_ANSWERS` PRIMARY KEY (
	`id`
);

ALTER TABLE `chat_messages` ADD CONSTRAINT `PK_CHAT_MESSAGES` PRIMARY KEY (
	`id`
);
```
