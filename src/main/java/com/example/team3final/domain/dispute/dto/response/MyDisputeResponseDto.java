package com.example.team3final.domain.dispute.dto.response;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;

import java.time.LocalDateTime;

// 내 이의제기 목록 조회용 DTO
// 상세 조회(DisputeResponseDto)와 구분하는 이유:
//   목록 화면은 카드 형태로 간략 정보만 보여주면 되므로
//   adminComment, holdDeadlineAt 같은 상세 필드는 제외해 응답 크기를 줄임
public record MyDisputeResponseDto(

        Long disputeId,             // 이의제기 ID — 목록 클릭 시 상세 조회 API 경로에 사용
        Long matchId,               // 매칭 ID — 상세 조회 API 경로(/matches/{matchId}/disputes/me)에 사용
        DisputeType disputeType,    // 이의제기 사유 타입 (GPS_ERROR, QR_ERROR 등) — 목록 카드 타입 라벨
        DisputeStatus status,       // 현재 처리 상태 (SUBMITTED / UNDER_REVIEW / HOLD 등) — 목록 카드 상태 뱃지
        LocalDateTime submittedAt   // 제출 시각 — 목록 카드 날짜 표시용
) {
    // Dispute 엔티티 → DTO 변환 정적 팩토리 메서드
    // 호출 쪽에서 new 로 직접 생성하지 않고 from() 을 쓰는 이유:
    //   → 엔티티 필드가 바뀌어도 여기 한 곳만 수정하면 됨 (유지보수 일원화)
    public static MyDisputeResponseDto from(Dispute dispute) {
        return new MyDisputeResponseDto(
                dispute.getId(),          // 이의제기 PK
                dispute.getMatchId(),     // 매칭 ID
                dispute.getDisputeType(), // 사유 타입
                dispute.getStatus(),      // 처리 상태
                dispute.getCreatedAt()    // 제출 시각 (BaseTimeEntity.createdAt)
        );
    }
}