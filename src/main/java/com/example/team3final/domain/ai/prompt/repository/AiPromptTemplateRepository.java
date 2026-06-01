package com.example.team3final.domain.ai.prompt.repository;

import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.prompt.entity.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;


/**
 * AI 프롬프트 템플릿 메타데이터를 조회하고 저장하는 Repository입니다.
 *
 * 프롬프트 본문은 파일로 관리하고,
 * 이 Repository는 어떤 프롬프트 타입이 어떤 파일명과 버전을 사용할지
 * DB 메타데이터를 기준으로 조회합니다.
 *
 * 활성 프롬프트 조회를 통해 코드에 프롬프트 파일 경로를 하드코딩하지 않고,
 * DB에서 선택된 프롬프트 버전을 사용할 수 있게 합니다.
 */
public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {

    Optional<AiPromptTemplate> findByPromptTypeAndActiveTrue(AiPromptType promptType);

    List<AiPromptTemplate> findByPromptType(AiPromptType promptType);

    boolean existsByPromptTypeAndVersion(AiPromptType promptType, String version);
}
