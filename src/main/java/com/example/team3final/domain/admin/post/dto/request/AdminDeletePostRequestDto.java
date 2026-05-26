package com.example.team3final.domain.admin.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AdminDeletePostRequestDto {

    @NotBlank(message = "삭제 사유는 필수입니다.")
    private String reason;
}
