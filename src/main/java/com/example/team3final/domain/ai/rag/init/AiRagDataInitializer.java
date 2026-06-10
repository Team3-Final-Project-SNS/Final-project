package com.example.team3final.domain.ai.rag.init;

import com.example.team3final.domain.ai.rag.dto.AiRagIndexResponseDto;
import com.example.team3final.domain.ai.rag.service.AiRagEtlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 실행 시 classpath RAG 문서를 자동 색인하는 초기화 Runner입니다.
 *
 * AiRagEtlServiceImpl이 문서별 content hash를 비교하므로,
 * 서버를 다시 켜도 변경되지 않은 문서는 embedding/vector_store에 중복 저장되지 않습니다.
 */
@Slf4j
@Profile({"prod", "docker", "local"})
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.ai.rag-store.enabled",
        havingValue = "true"
)
public class AiRagDataInitializer implements ApplicationRunner {

    private final AiRagEtlService aiRagEtlService;

    @Override
    public void run(ApplicationArguments args) {
        AiRagIndexResponseDto result = aiRagEtlService.indexClasspathDocuments();
        log.info("[AiRagDataInitializer] RAG 자동 색인 완료. sourceDocuments={}, chunks={}, message={}",
                result.sourceDocumentCount(),
                result.chunkCount(),
                result.message());
    }
}
