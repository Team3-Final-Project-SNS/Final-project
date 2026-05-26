package com.example.team3final.domain.admin.post.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AdminDeletePostRequestDto {

    @NotNull(message = "신고 ID는 필수입니다.")
    private Long reportId;

    @NotBlank(message = "삭제 사유는 필수입니다.")
    private String reason;
}
