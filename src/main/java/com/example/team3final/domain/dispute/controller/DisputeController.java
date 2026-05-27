package com.example.team3final.domain.dispute.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/matches")
public class DisputeController {

    private final DisputeService disputeService;

    @PostMapping("/{matchId}/disputes")
    public ResponseEntity<ApiResponseDto<CreateDisputeResponseDto>> createDispute(

            // @AuthenticationPrincipal: 인증 필터가 JWT 토큰을 검증한 뒤 만들어 둔
            //   로그인 사용자 정보(UserDetailsImpl)를 그대로 꽂아준다.
            //   → 요청자가 "누구인지"를 토큰에서 가져오므로, 클라이언트가 보낸 userId를
            //     그냥 믿지 않는다(위변조 방지). 본문에 userId를 안 받는 이유가 이것.
            @AuthenticationPrincipal UserDetailsImpl userDetails,

            // @PathVariable: URL 경로의 {matchId} 부분을 메서드 인자로 받는다.
            @PathVariable Long matchId,

            // @Valid: RequestDto에 붙은 검증 어노테이션(@NotBlank, @Size 등)을 실행한다.
            //   검증 실패 시 서비스까지 안 가고 여기서 막혀 400 응답이 나간다(빠른 차단).
            // @RequestBody: 요청 본문(JSON)을 CreateDisputeRequestDto 객체로 변환한다.
            @Valid @RequestBody CreateDisputeRequestDto request
    ) {
        // JWT 토큰에서 검증된 userId 추출 (당사자 검증용)
        Long userId = userDetails.getUserId();

        // 실제 비즈니스 로직(검증 + 저장)은 전부 서비스에 위임한다.
        CreateDisputeResponseDto response = disputeService.createDispute(matchId, userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }
}
