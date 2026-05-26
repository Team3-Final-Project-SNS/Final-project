package com.example.team3final.domain.ai.prompt.repository;

import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.prompt.entity.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findByPromptTypeAndActiveTrue(AiPromptType promptType);

    boolean existsByPromptTypeAndVersion(AiPromptType promptType, String version);
}