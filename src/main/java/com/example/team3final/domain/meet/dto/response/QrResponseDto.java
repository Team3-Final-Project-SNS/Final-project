package com.example.team3final.domain.meet.dto.response;

import java.time.LocalDateTime;

// 등록자 QR 조회 응답 DTO
// 그룹 만남에서는 QR이 Match 단위가 아니라 Post 단위로 공유
public record QrResponseDto (

        Long postId,
        String qrToken,
        LocalDateTime qrExpiresAt
) {
    public static QrResponseDto of(
            Long postId,
            String qrToken,
            LocalDateTime qrExpiresAt) {
        return new QrResponseDto(
                postId,
                qrToken,
                qrExpiresAt
        );
    }
}
