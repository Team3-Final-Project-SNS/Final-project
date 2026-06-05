package com.example.team3final.domain.ai.rag.service;

import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.rag.dto.AiRagSearchResultDto;

import java.util.List;

/**
 * AI 도메인 공통 RAG 검색 인터페이스입니다.
 *
 * 각 AI 기능은 SUPPORT, REPORT, MATCHING 같은 feature를 넘겨
 * 자기 도메인 정책 문서만 검색하고, 검색 결과를 프롬프트 컨텍스트로 주입합니다.
 */
public interface AiRagRetrieverService {

    List<AiRagSearchResultDto> search(
            String question,
            AiFeature feature,
            int topK,
            double similarityThreshold
    );
}
