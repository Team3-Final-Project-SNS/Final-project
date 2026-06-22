package com.example.team3final.domain.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AdminSuspendUserRequestDto {

    @NotBlank(message = "정지 사유는 필수입니다.")
    @Size(max = 500, message = "정지 사유는 최대 500자입니다.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9\\s]+$", message = "정지 사유는 한글, 영문, 숫자, 공백만 사용할 수 있습니다.")
    private String reason;

}
