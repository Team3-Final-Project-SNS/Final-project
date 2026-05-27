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

@Service
@RequiredArgsConstructor
public class AiPromptFileService {

    private final AiPromptTemplateRepository aiPromptTemplateRepository;
    private final AiProperties aiProperties;

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
