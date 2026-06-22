package com.example.team3final.domain.admin.report.dto.request;

import com.example.team3final.domain.report.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class AdminProcessReportRequestDto {

    // 처리 결과 -> ACCEPTED(채택) 또는 REJECTED(기각) 만 허용
    @NotNull(message = "처리 상태는 필수입니다.")
    private ReportStatus reportStatus;

    // 처리 사유 (선택 입력, 없어도 상관 X)
    @Pattern(regexp = "^\\s*$|.*[가-힣A-Za-z0-9].*", message = "처리 사유에는 한글, 영문, 숫자 중 하나 이상이 포함되어야 합니다.")
    @Size(max = 1000, message = "처리 사유는 최대 1000자까지 입력할 수 있습니다.")
    private String comment;
}
