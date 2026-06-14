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

@Profile({"prod", "docker", "local"})
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
                true,
                "한끼팟 매칭 AI LLM 주도 Tool Calling 프롬프트"
        );

        saveIfMissing(
                AiPromptType.MATCHING_CHAT,
                AiFeature.MATCHING,
                "v2",
                "matching-chat-v2.st",
                true,
                "한끼팟 매칭 AI 조건 반영 강화 프롬프트"
        );

        // v3는 게시글 추천용 pgvector 후보 검색과 이전 추천 후보 제한 규칙을 포함한 매칭 AI 프롬프트입니다.
        saveIfMissing(
                AiPromptType.MATCHING_CHAT,
                AiFeature.MATCHING,
                "v3",
                "matching-chat-v3.st",
                true,
                "한끼팟 매칭 AI pgvector 보조 인덱스 기반 추천 프롬프트"
        );

        saveIfMissing(
                AiPromptType.SUPPORT_CHAT,
                AiFeature.SUPPORT,
                "v1",
                "support-chat-v1.st",
                true,
                "한끼팟 고객센터 AI 기본 프롬프트"
        );

        saveIfMissing(
                AiPromptType.SUPPORT_CHAT,
                AiFeature.SUPPORT,
                "v2",
                "support-chat-v2.st",
                true,
                "한끼팟 고객센터 AI 예외 상황 안내 강화 프롬프트"
        );

        saveIfMissing(
                AiPromptType.REPORT_SUMMARY,
                AiFeature.REPORT,
                "v1",
                "report-summary-v1.st",
                true,
                "관리자 신고 AI 분석 프롬프트"
        );
    }

    private void saveIfMissing(
            AiPromptType promptType,
            AiFeature feature,
            String version,
            String fileName,
            boolean active,
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
                        .active(active)
                        .description(description)
                        .build()
        );
    }
}
