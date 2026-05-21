package com.example.team3final.domain.match.dto.response;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;

import java.time.LocalDateTime;

public record GetMatchesResponseDto(

        Long matchId,               // 매칭 ID
        Long postId,                // 게시글 ID
        Long opponentId,                // 상대방 userId
        String opponentNickname,        // 상대방 닉네임
        String opponentMajor,           // 상대방 학과
        String opponentStudentNumber,   // 상대방 학번
        LocalDateTime meetAt,   // postMatchInfo.meetAt()
        String placeName,       // postMatchInfo.placeName()
        // ===== 내 예치 포인트 =====
        // 내가 등록자면 postMatchInfo.authorDeposit()
        // 내가 신청자면 match.getApplicantDeposit()
        // → 서비스에서 판단 후 결정해서 넘김
        int myDeposit,
        MatchStatus status,         // 매칭 상태
        Long chatRoomId,            // 채팅방 ID (Chat 도메인, 미구현 시 null)
        LocalDateTime matchedAt,    // 매칭 확정 시각
        LocalDateTime completedAt   // 만남 완료 시각 (완료 전 null)
) {
    public static GetMatchesResponseDto of(
            Match match,
            Long opponentId,
            String opponentNickname,
            String opponentMajor,
            String opponentStudentNumber,
            LocalDateTime meetAt,
            String placeName,
            int myDeposit,
            Long chatRoomId
    ) {
        return new GetMatchesResponseDto(
                match.getId(),
                match.getPostId(),
                opponentId,
                opponentNickname,
                opponentMajor,
                opponentStudentNumber,
                meetAt,
                placeName,
                myDeposit,
                match.getStatus(),
                chatRoomId,
                match.getMatchedAt(),
                match.getCompletedAt()
        );
    }

}
