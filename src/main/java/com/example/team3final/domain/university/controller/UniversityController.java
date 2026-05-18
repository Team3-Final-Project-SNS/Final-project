package com.example.team3final.domain.university.controller;


import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class UniversityController {

    private final UniversityService universityService;

    @GetMapping("/universities")
    public ResponseEntity<ApiResponseDto<List<UniversityResponseDto>>> getUniversities() {
        return ResponseEntity.ok(
                ApiResponseDto.success(universityService.getUniversities())
        );

    }
}
