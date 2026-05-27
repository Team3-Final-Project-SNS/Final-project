package com.example.team3final.domain.dispute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateDisputeRequestDto {

    /**
     * 이의제기 사유.
     * - @NotBlank: null/빈문자열/공백만 입력 차단 (명세상 필수값)
     * - @Size(max=1000): 명세상 최대 1000자
     */
    @NotBlank(message = "이의제기 사유는 필수입니다.")
    @Size(max = 1000, message = "이의제기 사유는 최대 1000자까지 입력할 수 있습니다.")
    private String reason;

    @Builder
    private CreateDisputeRequestDto(String reason) {
        this.reason = reason;
    }
}
