package com.example.team3final.domain.inquiry.dto.request;

import com.example.team3final.domain.inquiry.enums.InquiryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInquiryRequestDto {

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    @NotNull(message = "문의 유형을 선택해주세요.")
    private InquiryType type;
}
