package com.example.team3final.domain.ai.prompt.repository;

import com.example.team3final.domain.ai.prompt.entity.AiPromptHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiPromptHistoryRepository extends JpaRepository<AiPromptHistory, Long> {

    List<AiPromptHistory> findAllByPromptTemplateIdOrderByCreatedAtDesc(Long promptTemplateId);
}
