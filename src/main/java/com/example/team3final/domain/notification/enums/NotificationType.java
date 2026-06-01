package com.example.team3final.domain.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    MATCH_APPLIED("게시글 신청"),               // 1. 게시글에 누군가 신청했을 때
    MATCH_CONFIRMED("매칭 확정"),               // 16. 매칭이 확정되었을 때
    MATCH_CANCELLED("매칭 취소"),               // 2. 게시글 신청을 취소했을 때
    MEET_REMINDER("만남 시간 알림"),             // 6/7. 만남 30분/15분 전 알림
    MEET_IMMINENT("만남 시간 임박"),             // 5. 만남 시간이 임박했을 때
    CHAT_RECEIVED("채팅 메시지 수신"),           // 3. 채팅방 메시지가 왔을 때
    PLACE_VERIFIED("장소 인증 완료"),            // 4. 상대가 장소 인증을 완료했을 때
    NO_SHOW_WARNING("노쇼 예정"),               // 9. 노쇼 예정 알림
    NO_SHOW_CONFIRMED("노쇼 확정"),             // 8. 노쇼 확정 안내
    DISPUTE_RESULT("이의제기 결과"),             // 17. 이의제기 판정 결과
    DISPUTE_PENDING("이의제기 보류"),            // 24. 이의제기 보류 - 추가 증거 제출 요청
    POINT_CHANGED("포인트 변동"),               // 13/14. 신고 채택/후기 포인트 지급
    MANNER_TEMPERATURE_CHANGED("매너 온도 변경"), // 27. 후기 작성으로 매너 온도 변경 시 대상자에게
    REPORT_RESULT("신고 처리 결과"),             // 10. 신고 처리 결과 알림
    DISPUTE_SUBMITTED("이의제기 접수"),          // 11. 관리자에게 이의제기가 왔을 때
    REPORT_SUBMITTED("신고 접수"),              // 25. 신고 접수 시 관리자에게
    INQUIRY_SUBMITTED("문의 접수"),             // 26. 문의 접수 시 관리자에게
    INQUIRY_ANSWERED("문의 답변 완료"),          // 15. 문의 답변 완료
    MEET_EXTEND_REQUESTED("만남 시간 연장 요청"), // 18. 만남 시간 연장 요청이 왔을 때
    MEET_EXTEND_ACCEPTED("만남 시간 연장 수락"),  // 19. 만남 시간 연장이 수락되었을 때
    MEET_EXTEND_REJECTED("만남 시간 연장 거절"),  // 20. 만남 시간 연장이 거절되었을 때
    MEET_EXTEND_EXPIRED("만남 시간 연장 만료"),   // 21. 만남 시간 연장 요청이 만료되었을 때
    SYSTEM("시스템 공지");                      // 12. 공지 / 22. 게시글 삭제 / 23. 게시글 만료 / 제재 안내

    private final String description;
}