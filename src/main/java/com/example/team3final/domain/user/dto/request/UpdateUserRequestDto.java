package com.example.team3final.domain.user.dto.request;

import jakarta.validation.constraints.Size;
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
    private String nickname;

    @Size(max = 100, message = "학과는 100자 이하여야합니다.")
    private String major;
}
