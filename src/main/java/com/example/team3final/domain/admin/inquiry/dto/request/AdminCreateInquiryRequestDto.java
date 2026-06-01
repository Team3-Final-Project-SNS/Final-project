package com.example.team3final.domain.admin.inquiry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminCreateInquiryRequestDto {

    @NotBlank(message = "답변 내용은 필수입니다.")
    @Size(max = 2000, message = "답변은 2000자 이내여야 합니다.")
    String content;
}
