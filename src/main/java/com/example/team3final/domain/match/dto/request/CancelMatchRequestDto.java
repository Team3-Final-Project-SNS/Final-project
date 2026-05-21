package com.example.team3final.domain.match.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CancelMatchRequestDto {

    @Size(max = 200, message = "취소 사유는 200자를 초과할 수 없습니다.")
    private String reason;
}
