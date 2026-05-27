package com.example.team3final.domain.match.dto.response;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;

import java.time.LocalDateTime;

public record CreateMatchResponseDto(
        Long matchId,
        Long postId,
        Long authorId,
        String authorNickname,
        Long applicantId,
        String applicantNickname,
        int authorDeposit,
        int applicantDeposit,
        MatchStatus status,
        Long chatRoomId,
        LocalDateTime matchedAt
) {
    /**
     * 응답 DTO 생성 정적 팩토리
     *
     * @param match              생성된 Match 엔티티
     * @param authorId           게시글 작성자 ID (Post에서 추출)
     * @param authorDeposit      등록자 예치금 (Post에서 추출)
     * @param authorNickname     등록자 닉네임 (User에서 조회)
     * @param applicantNickname  신청자 닉네임 (User에서 조회)
     * @param chatRoomId         채팅방 ID (Chat에서 받아옴, 미구현 시 null)
     */
    public static CreateMatchResponseDto of(
            Match match,
            Long authorId,
            int authorDeposit,
            String authorNickname,
            String applicantNickname,
            Long chatRoomId
    ) {
        return new CreateMatchResponseDto(
                match.getId(),
                match.getPostId(),
                authorId,
                authorNickname,
                match.getApplicantId(),
                applicantNickname,
                authorDeposit,
                match.getApplicantDeposit(),
                match.getStatus(),
                chatRoomId,
                match.getCreatedAt()
        );
    }
}
