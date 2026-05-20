package com.example.team3final.domain.post.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequestDto {

    @Future(message = "만남 희망 시간은 현재 이후여야 합니다.")
    private LocalDateTime meetAt;

    @Size(max = 200, message = "장소명은 200자를 초과할 수 없습니다.")
    private String placeName;

    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
    private BigDecimal placeLat;

    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
    private BigDecimal placeLng;

    @Size(max = 500, message = "내용은 500자를 초과할 수 없습니다.")
    private String content;

    @Min(value = 200, message = "책임비 포인트는 최소 200P 이상이어야 합니다.")
    private Integer authorDeposit;
}
