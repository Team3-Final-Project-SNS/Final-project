package com.example.team3final.common.dto.response;

public record ErrorResponseDto(
        boolean success,
        String code,
        String message
) {
    public static ErrorResponseDto of(String code, String message) {
        return new ErrorResponseDto(false, code, message);
    }
}
