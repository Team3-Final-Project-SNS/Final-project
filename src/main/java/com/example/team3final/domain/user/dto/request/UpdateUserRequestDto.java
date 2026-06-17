package com.example.team3final.domain.user.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequestDto {
    private String currentPassword;

    @Size(min = 8, max = 20, message = "비밀번호 8자 이상 20자 이하여야합니다.")
    private String newPassword;

    @Size(min = 2, max = 30, message = "닉네임은 2자이상 30자 이하여야합니다.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.")
    private String nickname;

    @Size(max = 100, message = "학과는 100자 이하여야합니다.")
    @Pattern(regexp = "^[가-힣A-Za-z0-9\\s]+$", message = "학과는 한글, 영문, 숫자, 공백만 사용할 수 있습니다.")
    private String major;
}
