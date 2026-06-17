package com.example.team3final.domain.match.dto.response;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

public record GetMatchesResponseDto(

        Long matchId,
        Long postId,
        Long opponentId,
        String opponentNickname,
        String opponentMajor,
        String opponentStudentNumber,
        LocalDateTime meetAt,
        String placeName,
        int currentApplicants,
        int maxApplicants,
        int myDeposit,
        boolean isAuthor,
        // 그룹 매칭 목록 화면용 전체 참여자 목록
        List<MatchParticipantDto> participants,
        MatchStatus status,
        Long chatRoomId,
        LocalDateTime matchedAt,
        LocalDateTime completedAt
) {
    public static GetMatchesResponseDto of(
            Match match,
            Long opponentId,
            String opponentNickname,
            String opponentMajor,
            String opponentStudentNumber,
            LocalDateTime meetAt,
            String placeName,
            int currentApplicants,
            int maxApplicants,
            int myDeposit,
            boolean isAuthor,
            List<MatchParticipantDto> participants,
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
                currentApplicants,
                maxApplicants,
                myDeposit,
                isAuthor,
                participants,
                match.getStatus(),
                chatRoomId,
                match.getCreatedAt(),
                match.getCompletedAt()
        );
    }
}
