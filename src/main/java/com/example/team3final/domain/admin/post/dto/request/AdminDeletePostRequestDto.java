package com.example.team3final.domain.admin.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class AdminDeletePostRequestDto {

    private Long reportId;

    @NotBlank(message = "삭제 사유는 필수입니다.")
    @Pattern(regexp = ".*[가-힣A-Za-z0-9].*", message = "삭제 사유에는 한글, 영문, 숫자 중 하나 이상이 포함되어야 합니다.")
    private String reason;
}
