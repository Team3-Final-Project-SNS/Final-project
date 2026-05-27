package com.example.team3final.domain.ai.prompt.service;


import com.example.team3final.common.config.AiProperties;
import com.example.team3final.common.exception.AiException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.prompt.entity.AiPromptTemplate;
import com.example.team3final.domain.ai.prompt.repository.AiPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;


/**
 * AI 프롬프트 파일을 조회하고 렌더링하는 서비스입니다.
 *
 * DB의 AiPromptTemplate에서 활성 프롬프트 파일명을 조회한 뒤,
 * 외부 디렉토리 또는 classpath 리소스에서 프롬프트 파일을 읽어
 * Spring AI PromptTemplate으로 변수 값을 치환합니다.
 *
 * 프롬프트 본문을 코드에 하드코딩하지 않고 파일로 분리하며,
 * 어떤 파일을 사용할지는 DB 메타데이터로 관리합니다.
 */
@Service
@RequiredArgsConstructor
public class AiPromptFileService {

    private final AiPromptTemplateRepository aiPromptTemplateRepository;
    private final AiProperties aiProperties;


    /**
     * 프롬프트 타입에 해당하는 활성 템플릿을 조회하고,
     * 전달받은 변수 값을 치환하여 최종 프롬프트 문자열을 생성합니다.
     *
     * @param type 프롬프트 용도
     * @param variables 프롬프트 변수 맵
     * @return 변수 치환이 완료된 프롬프트 문자열
     * @throws AiException 활성 템플릿이 없거나 프롬프트 파일을 읽지 못한 경우
     */
    public String render(AiPromptType type, Map<String, Object> variables) {
        AiPromptTemplate promptTemplate = aiPromptTemplateRepository.findByPromptTypeAndActiveTrue(type)
                .orElseThrow(() -> new AiException(ErrorCode.AI_PROMPT_TEMPLATE_NOT_FOUND));

        try {
            String templateContent = loadTemplateContent(promptTemplate.getFileName());

            PromptTemplate template = PromptTemplate.builder()
                    .template(templateContent)
                    .build();

            return template.render(variables);
        } catch (Exception e) {
            throw new AiException(ErrorCode.AI_PROMPT_FILE_READ_FAILED);
        }
    }


    /**
     * 프롬프트 파일 내용을 읽습니다.
     *
     * 먼저 app.ai.prompt.base-path에 설정된 외부 디렉토리에서 파일을 찾고,
     * 파일이 없으며 fallbackToClasspath=true이면 classpath의 prompts 디렉토리에서 다시 조회합니다.
     *
     * 이를 통해 운영 환경에서는 외부 파일 기반 프롬프트 교체를 지원하고,
     * 로컬 또는 초기 개발 환경에서는 classpath 기본 프롬프트를 fallback으로 사용할 수 있습니다.
     *
     * @param fileName 프롬프트 파일명
     * @return 프롬프트 파일 내용
     * @throws IOException 외부 파일과 classpath fallback 모두에서 파일을 찾지 못한 경우
     */
    private String loadTemplateContent(String fileName) throws IOException {
        Path externalPath = Path.of(
                aiProperties.getPrompt().getBasePath(),
                fileName
        );

        if (Files.exists(externalPath)) {
            return Files.readString(externalPath);
        }

        if (aiProperties.getPrompt().isFallbackToClasspath()) {
            ClassPathResource resource = new ClassPathResource("prompts/" + fileName);

            if (resource.exists()) {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            }
        }

        throw new IOException("AI prompt file not found: " + fileName);
    }
}
