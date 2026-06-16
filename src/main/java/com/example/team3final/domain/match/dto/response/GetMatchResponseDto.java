package com.example.team3final.domain.match.dto.response;


import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.post.entity.Post;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GetMatchResponseDto(
        Long matchId,
        Long postId,
        Long authorId,
        String authorNickname,
        String authorMajor,
        String authorStudentNumber,
        Long applicantId,
        String applicantNickname,
        String applicantMajor,
        String applicantStudentNumber,
        LocalDateTime meetAt,
        String placeName,
        BigDecimal placeLat,
        BigDecimal placeLng,
        int authorDeposit,
        int applicantDeposit,
        int currentApplicants,
        int maxApplicants,
        BigDecimal authorMannerTemperature,
        // 그룹 매칭 상세 화면용 전체 참여자 목록
        List<MatchParticipantDto> participants,
        MatchStatus status,
        Long chatRoomId,
        LocalDateTime matchedAt,
        LocalDateTime completedAt
) {
    /**
     * 여러 도메인 데이터를 합성해 응답 DTO 생성
     *
     * @param match
     * @param post
     * @param authorNickname
     * @param authorMajor
     * @param authorStudentNumber
     * @param applicantNickname
     * @param applicantMajor
     * @param applicantStudentNumber
     * @param chatRoomId
     */
    public static GetMatchResponseDto of(
            Match match,
            Post post,
            String authorNickname,
            String authorMajor,
            String authorStudentNumber,
            String applicantNickname,
            String applicantMajor,
            String applicantStudentNumber,
            BigDecimal authorMannerTemperature,
            List<MatchParticipantDto> participants,
            LocalDateTime meetAt,
            Long chatRoomId
    ) {
        return new GetMatchResponseDto(
                match.getId(),
                match.getPostId(),
                post.getAuthorId(),
                authorNickname,
                authorMajor,
                authorStudentNumber,
                match.getApplicantId(),
                applicantNickname,
                applicantMajor,
                applicantStudentNumber,
                meetAt,
                post.getPlaceName(),
                post.getPlaceLat(),
                post.getPlaceLng(),
                post.getAuthorDeposit(),
                match.getApplicantDeposit(),
                Math.max(post.getCurrentApplicants(), 1),
                Math.max(post.getMaxApplicants(), 2),
                authorMannerTemperature,
                participants,
                match.getStatus(),
                chatRoomId,
                match.getCreatedAt(),
                match.getCompletedAt()
        );
    }
}
