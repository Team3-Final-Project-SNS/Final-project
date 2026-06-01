package com.example.team3final.domain.admin.dispute.dto.request;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminOverrideDisputeStatusRequestDto {

    // 변경할 목표 상태
    @NotNull(message = "변경할 상태는 필수입니다.")
    private DisputeStatus status;

    // 강제 변경 사유
    @NotBlank(message = "변경 사유는 필수입니다.")
    @Size(max = 1000, message = "변경 사유는 최대 1000자까지 입력할 수 있습니다.")
    private String comment;
}
