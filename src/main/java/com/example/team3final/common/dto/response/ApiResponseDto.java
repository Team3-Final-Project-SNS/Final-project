package com.example.team3final.common.dto.response;

public record ApiResponseDto<T>(
        boolean success,
        String code,
        String message,
        T data
) {
    // 성공 - 응답 데이터 O
    public static <T> ApiResponseDto<T> success(T data) {
        return new ApiResponseDto<>(true, "SUCCESS", "요청이 성공했습니다.", data);
    }

    // 성공 - 응답 데이터 없을때
    public static ApiResponseDto<Void> successWithNoContent() {
        return new ApiResponseDto<>(true, "SUCCESS", "요청이 성공했습니다.", null);
    }
}
