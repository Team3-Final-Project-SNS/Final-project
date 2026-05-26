package com.example.team3final.domain.admin.meet.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import com.example.team3final.domain.admin.meet.service.AdminMeetVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminMeetVerificationController {

    private final AdminMeetVerificationService adminMeetVerificationService;

    // 노쇼 후보군 조회
    @GetMapping("/no-show-candidates")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminNoShowCandidateResponseDto>>> getNoShowCandidates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        return ResponseEntity.ok(ApiResponseDto.success(
                adminMeetVerificationService.getNoShowCandidates(pageable)
        ));
    }
}
