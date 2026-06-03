package com.example.team3final.domain.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminReinstateUserRequestDto {

    // 정지 해제 사유 — 관리자가 반드시 입력 (오판정 정정, 이의제기 수용 등)
    @NotBlank(message = "정지 해제 사유는 필수입니다.")
    private String reason;
}
