package com.example.team3final.domain.match.dto.response;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;

/**
 * 매칭 정보 조회용 DTO — 도메인 간 호출 전용
 *
 * 사용처:
 * - 채팅: 채팅방 입장 권한 검증 (등록자/신청자 ID 확인)
 * - GPS 인증: 매칭 당사자 검증, 상태 검증
 *
 * ※ Match 엔티티에 없는 authorId는 함께 받아서 채움 (Post.authorId)
 *    → 매칭만으로 등록자 ID를 알 수 없으므로 호출자가 Post까지 조회 후 합성
 */
public record MatchInfoDto(
        Long matchId,
        Long postId,
        Long applicantId,
        MatchStatus status
) {
    /**
     * 사용자가 이 매칭의 "당사자"인지 검증
     *
     * - 등록자(authorId)이거나 신청자(applicantId)이면 true
     * - authorId는 Match에 없으므로 호출자가 Post에서 꺼내 인자로 전달
     *
     * 사용 예 (채팅 권한 검증):
     *   if (!matchInfo.isParticipant(userId, post.getAuthorId())) {
     *       throw new ServiceException(ErrorCode.CHAT_NOT_PARTICIPANT);
     *   }
     */
    public boolean isParticipant(Long userId, Long authorId) {
        return userId.equals(authorId) || userId.equals(applicantId);
    }

    /**
     * 사용자가 신청자인지 검증 (등록자/신청자 구분 시 사용)
     *
     * 사용 예 (QR 스캔은 신청자만 가능):
     *   if (!matchInfo.isApplicant(userId)) {
     *       throw new ServiceException(ErrorCode.SCAN_NOT_APPLICANT);
     *   }
     */
    public boolean isApplicant(Long userId) {
        return userId.equals(applicantId);
    }

    /**
     * Entity → DTO 변환 정적 팩토리
     */
    public static MatchInfoDto from(Match match) {
        return new MatchInfoDto(
                match.getId(),
                match.getPostId(),
                match.getApplicantId(),
                match.getStatus()
        );
    }
}
