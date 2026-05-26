package com.example.team3final.domain.ai.prompt.service;


import com.example.team3final.common.config.AiProperties;
import com.example.team3final.common.exception.AiException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.prompt.entity.AiPromptTemplate;
import com.example.team3final.domain.ai.prompt.repository.AiPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiPromptFileService {

    private final AiPromptTemplateRepository aiPromptTemplateRepository;
    private final AiProperties aiProperties;

    public String render(AiPromptType type, Map<String, Object> variables) {
        AiPromptTemplate promptTemplate = aiPromptTemplateRepository.findByPromptTypeAndActiveTrue(type)
                .orElseThrow(() -> new AiException(ErrorCode.AI_PROMPT_TEMPLATE_NOT_FOUND));

        try {
            Path path = Path.of(
                    aiProperties.getPrompt().getBasePath(),
                    promptTemplate.getFileName()
            );

            String templateContent = Files.readString(path);

            PromptTemplate template = PromptTemplate.builder()
                    .renderer(StTemplateRenderer.builder()
                            .startDelimiterToken('<')
                            .endDelimiterToken('>')
                            .build())
                    .template(templateContent)
                    .build();

            return template.render(variables);
        } catch (Exception e) {
            throw new AiException(ErrorCode.AI_PROMPT_FILE_READ_FAILED);
        }
    }
}