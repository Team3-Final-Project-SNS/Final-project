package com.example.team3final.domain.admin.dispute.dto.request;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminJudgeDisputeRequestDto {

    @NotNull(message = "판정 결과는 필수입니다.")
    DisputeStatus status;

    @NotBlank(message = "판정 내용은 필수입니다.")
    @Size(max = 1000, message = "판정 내용은 1000자 이내여야 합니다.")
    String comment;
}
