package com.example.team3final.domain.match.dto.response;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.user.dto.response.UserInfoDto;

import java.time.LocalDateTime;

public record MatchParticipantDto(
        Long userId,
        Long matchId,
        String nickname,
        String major,
        String studentNumber,
        String role,
        MatchStatus status,
        LocalDateTime matchedAt,
        LocalDateTime completedAt
) {

    // 매칭 응답에서 작성자와 신청자를 같은 참여자 목록으로 내려주기 위한 역할 값
    private static final String AUTHOR_ROLE = "AUTHOR";
    private static final String APPLICANT_ROLE = "APPLICANT";

    public static MatchParticipantDto author(Long authorId, UserInfoDto authorInfo) {
        return new MatchParticipantDto(
                authorId,
                null,
                authorInfo.nickname(),
                authorInfo.major(),
                authorInfo.studentNumber(),
                AUTHOR_ROLE,
                null,
                null,
                null
        );
    }

    public static MatchParticipantDto applicant(Match match, UserInfoDto applicantInfo) {
        return new MatchParticipantDto(
                match.getApplicantId(),
                match.getId(),
                applicantInfo.nickname(),
                applicantInfo.major(),
                applicantInfo.studentNumber(),
                APPLICANT_ROLE,
                match.getStatus(),
                match.getCreatedAt(),
                match.getCompletedAt()
        );
    }
}
