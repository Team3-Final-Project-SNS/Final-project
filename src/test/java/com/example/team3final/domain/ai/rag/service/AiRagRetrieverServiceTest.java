package com.example.team3final.domain.ai.rag.service;

import com.example.team3final.domain.ai.rag.dto.AiRagSearchResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI RAG 검색 서비스 단위 테스트")
class AiRagRetrieverServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private VectorStore vectorStore;

    @Test
    @DisplayName("검색 문서의 본문과 메타데이터를 RAG 검색 응답으로 변환한다")
    void toResult_shouldMapDocumentToSearchResult() {
        AiRagRetrieverServiceImpl aiRagRetrieverService =
                new AiRagRetrieverServiceImpl(chatClientBuilder, vectorStore);
        Document document = Document.builder()
                .text("환불 정책 안내")
                .metadata(Map.of(
                        "title", "결제 정책",
                        "source", "payment.md",
                        "feature", "SUPPORT"
                ))
                .score(0.9)
                .build();

        AiRagSearchResultDto result = ReflectionTestUtils.invokeMethod(
                aiRagRetrieverService,
                "toResult",
                document
        );

        assertThat(result.content()).isEqualTo("환불 정책 안내");
        assertThat(result.source().title()).isEqualTo("결제 정책");
        assertThat(result.source().source()).isEqualTo("payment.md");
        assertThat(result.source().feature()).isEqualTo("SUPPORT");
        assertThat(result.score()).isEqualTo(0.9);
    }
}
