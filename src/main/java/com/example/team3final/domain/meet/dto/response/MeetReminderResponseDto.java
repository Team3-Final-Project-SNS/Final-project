package com.example.team3final.domain.meet.dto.response;

public record MeetReminderResponseDto(
        Long meetVerificationId,
        Long matchId,
        Long authorId,
        Long applicantId
) {}