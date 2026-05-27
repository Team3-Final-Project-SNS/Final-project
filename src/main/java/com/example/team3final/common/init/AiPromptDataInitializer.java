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
        if (aiPromptTemplateRepository.existsByPromptTypeAndVersion(AiPromptType.MATCHING_CHAT, "v1")) {
            return;
        }

        aiPromptTemplateRepository.save(
                AiPromptTemplate.builder()
                        .promptType(AiPromptType.MATCHING_CHAT)
                        .feature(AiFeature.MATCHING)
                        .version("v1")
                        .fileName("matching-chat.st")
                        .active(true)
                        .description("한끼팟 매칭 AI 기본 프롬프트")
                        .build()
        );
    }
}
