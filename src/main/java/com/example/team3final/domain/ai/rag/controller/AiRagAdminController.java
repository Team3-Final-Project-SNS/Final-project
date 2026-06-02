package com.example.team3final.domain.ai.rag.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.ai.rag.dto.AiRagIndexResponseDto;
import com.example.team3final.domain.ai.rag.service.AiRagEtlService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 로컬 개발용 RAG ETL 관리자 API입니다.
 *
 * resources/rag-docs 문서를 pgvector VectorStore에 색인하거나,
 * feature 메타데이터 기준으로 색인 데이터를 삭제할 때 사용합니다.
 * 운영 환경에서는 별도 관리자 권한과 배치 파이프라인으로 분리하는 것이 좋습니다.
 */
@Profile("rag-local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/rag")
@ConditionalOnBean(AiRagEtlService.class)
public class AiRagAdminController {

    private final AiRagEtlService aiRagEtlService;

    @PostMapping("/index/classpath")
    public ResponseEntity<ApiResponseDto<AiRagIndexResponseDto>> indexClasspathDocuments() {
        return ResponseEntity.ok(ApiResponseDto.success(aiRagEtlService.indexClasspathDocuments()));
    }

    @DeleteMapping("/index")
    public ResponseEntity<ApiResponseDto<AiRagIndexResponseDto>> clearByFeature(
            @RequestParam String feature
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(aiRagEtlService.clearByFeature(feature)));
    }
}
