package com.example.team3final.domain.post.dto.request;

import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreatePostRequestDto {

    // 만남 희망 시간
    @NotNull(message = "만남 희망 시간은 필수입니다.")
    @Future(message = "만남 희망 시간은 현재 이후여야 합니다.")
    private LocalDateTime meetAt;

    // 만남 장소명
    @NotBlank(message = "만남 장소명은 필수입니다.")
    @Size(max = 200, message = "만남 장소명은 200자 이하여야 합니다.")
    private String placeName;

    // 약속 장소 위도 (-90 ~ 90)
    @NotNull(message = "약속 장소 위도는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도는 -90 ~ 90 사이여야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 -90 ~ 90 사이여야 합니다.")
    private BigDecimal placeLat;

    // 약속 장소 경도 (-180 ~ 180)
    @NotNull(message = "약속 장소 경도는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도는 -180 ~ 180 사이여야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 -180 ~ 180 사이여야 합니다.")
    private BigDecimal placeLng;

    // 한마디
    @Size(max = 500, message = "한마디는 500자 이하여야 합니다.")
    private String content;

    // 책임비 포인트
    // Bean Validation으로 1차 검증, Service에서 정밀 검증
    @NotNull(message = "책임비 포인트는 필수입니다.")
    @Min(value = 200, message = "책임비 포인트는 최소 200P 이상이어야 합니다.")
    private Integer authorDeposit;

    @Builder
    private CreatePostRequestDto(LocalDateTime meetAt, String placeName,
                                 BigDecimal placeLat, BigDecimal placeLng,
                                 String content, Integer authorDeposit) {
        this.meetAt = meetAt;
        this.placeName = placeName;
        this.placeLat = placeLat;
        this.placeLng = placeLng;
        this.content = content;
        this.authorDeposit = authorDeposit;
    }
}
