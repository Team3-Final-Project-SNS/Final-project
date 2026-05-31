package com.example.team3final.common.init;

import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.prompt.entity.AiPromptTemplate;
import com.example.team3final.domain.ai.prompt.repository.AiPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Profile("local")
@Component
@RequiredArgsConstructor
public class AiPromptDataInitializer implements ApplicationRunner {

    private final AiPromptTemplateRepository aiPromptTemplateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        saveIfMissing(
                AiPromptType.MATCHING_CHAT,
                AiFeature.MATCHING,
                "v1",
                "matching-chat-v1.st",
                "한끼팟 매칭 AI 기본 프롬프트"
        );

        saveIfMissing(
                AiPromptType.REPORT_SUMMARY,
                AiFeature.REPORT,
                "v1",
                "report-summary-v1.st",
                "관리자 신고 AI 분석 프롬프트"
        );
    }

    private void saveIfMissing(
            AiPromptType promptType,
            AiFeature feature,
            String version,
            String fileName,
            String description
    ) {
        if (aiPromptTemplateRepository.existsByPromptTypeAndVersion(promptType, version)) {
            return;
        }

        aiPromptTemplateRepository.save(
                AiPromptTemplate.builder()
                        .promptType(promptType)
                        .feature(feature)
                        .version(version)
                        .fileName(fileName)
                        .active(true)
                        .description(description)
                        .build()
        );
    }
}
