package com.example.team3final.domain.university.controller;


import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "University", description = "대학 API - 회원가입 시 학교 목록 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class UniversityController {

    private final UniversityService universityService;

    // 대학 목록 조회
    @Operation(
            summary = "대학 목록 조회",
            description = """
                    서비스에 등록된 대학 목록을 조회합니다.
                    - 회원가입 화면에서 학교 선택 시 사용합니다.
                    - **인증 없이 호출 가능** (로그인 불필요)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "등록된 대학 없음 (UNIVERSITY_001)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "success": false,
                                      "code": "UNIVERSITY_001",
                                      "message": "조회 가능한 대학 목록이 없습니다.",
                                      "data": null
                                    }
                                    """)
                    )
            )
    })
    @GetMapping("/universities")
    public ResponseEntity<ApiResponseDto<List<UniversityResponseDto>>> getUniversities() {
        return ResponseEntity.ok(
                ApiResponseDto.success(universityService.getUniversities())
        );

    }
}
