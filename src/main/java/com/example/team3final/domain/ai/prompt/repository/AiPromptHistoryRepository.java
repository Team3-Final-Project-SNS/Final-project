package com.example.team3final.domain.ai.prompt.repository;

import com.example.team3final.domain.ai.prompt.entity.AiPromptHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


/**
 * AI 프롬프트 개선 이력을 조회하고 저장하는 Repository입니다.
 *
 * 프롬프트 변경 시 변경 이유, 토큰 변화, 출력 품질 변화,
 * 다음 개선 방향을 기록하여 프롬프트 엔지니어링 이력을 관리합니다.
 *
 * 발제 요구사항의 "프롬프트 개선 이력 최소 3회 기록"을
 * DB 데이터로 남기기 위해 사용합니다.
 */
public interface AiPromptHistoryRepository extends JpaRepository<AiPromptHistory, Long> {

    List<AiPromptHistory> findAllByPromptTemplateIdOrderByCreatedAtDesc(Long promptTemplateId);
}
