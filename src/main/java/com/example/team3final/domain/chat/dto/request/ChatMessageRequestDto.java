package com.example.team3final.domain.chat.dto.request;

import jakarta.persistence.GeneratedValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequestDto {

    private String content; // 메시지 내용 (senderId는 JWT 토큰에서 추출하므로 클라이언트에서 받지 않음)
}
