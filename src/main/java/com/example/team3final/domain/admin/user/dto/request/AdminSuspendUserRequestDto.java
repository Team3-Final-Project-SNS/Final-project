package com.example.team3final.domain.admin.user.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AdminSuspendUserRequestDto {

    @NotBlank(message = "정지 사유는 필수입니다.")
    @Size(max = 500, message = "정지 사유는 최대 500자입니다.")
    private String reason;

    // 정지 일수 (null = 영구정지, 양수여야 함)
    @Min(value = 1, message = "정지 일수는 1일 이상이어야 합니다.")
    private Integer days;
}
