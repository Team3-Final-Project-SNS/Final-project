package com.example.team3final.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WithdrawRequestDto {

    @NotBlank(message = "현재 비밀번호를 입력해주세요")
    private String password;
}
