package com.example.team3final.domain.admin.report.dto.request;

import com.example.team3final.domain.report.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AdminProcessReportRequestDto {

    // 처리 결과 -> ACCEPTED(채택) 또는 REJECTED(기각) 만 허용
    @NotNull(message = "처리 상태는 필수입니다.")
    private ReportStatus reportStatus;

    // 처리 사유 (선택 입력, 없어도 상관 X)
    private String comment;
}
