package com.example.team3final.domain.auth.dto.response;

public record LoginResponseDto (
        Long userId,
        String nickname,
        String accessToken
){
}
