package com.example.team3final.domain.report.dto.request;

import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequestDto {

    @NotNull(message = "신고 대상 유형은 필수입니다.")
    private ReportTargetType targetType;

    @NotNull(message = "신고 대상 ID는 필수입니다.")
    private Long targetId;

    @NotNull(message = "신고 사유는 필수입니다.")
    private ReportReason reason;

    @Size(max = 500, message = "상세 내용은 500자 이하여야 합니다.")
    private String detail;
}
