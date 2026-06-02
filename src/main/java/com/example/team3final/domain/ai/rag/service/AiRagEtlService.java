package com.example.team3final.domain.ai.rag.service;

import com.example.team3final.domain.ai.rag.dto.AiRagIndexResponseDto;

/**
 * RAG ETL 파이프라인 서비스 계약입니다.
 *
 * classpath의 rag-docs 문서를 읽어 chunk로 분리하고,
 * embedding/vector store에 저장하는 색인 작업을 담당합니다.
 */
public interface AiRagEtlService {

    AiRagIndexResponseDto indexClasspathDocuments();

    AiRagIndexResponseDto clearByFeature(String feature);
}
