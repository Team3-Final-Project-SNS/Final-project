package com.example.team3final.domain.ai.rag.service;

import com.example.team3final.domain.ai.rag.dto.AiRagIndexResponseDto;
import com.example.team3final.domain.ai.rag.repository.AiRagDocumentIndexRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI RAG ETL 서비스 단위 테스트")
class AiRagEtlServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    @Mock
    private AiRagDocumentIndexRepository aiRagDocumentIndexRepository;

    @InjectMocks
    private AiRagEtlServiceImpl aiRagEtlService;

    @Test
    @DisplayName("기능 단위 RAG 문서 삭제는 벡터 저장소와 인덱스 저장소에 위임한다")
    void clearByFeature_shouldDeleteVectorAndIndex() {
        AiRagIndexResponseDto response = aiRagEtlService.clearByFeature("SUPPORT");

        assertThat(response.message()).contains("SUPPORT");
        verify(vectorStore).delete("feature == 'SUPPORT'");
        verify(aiRagDocumentIndexRepository).deleteAllByFeature("SUPPORT");
    }
}
