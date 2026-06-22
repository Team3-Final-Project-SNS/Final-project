package com.example.team3final.domain.ai.rag.repository;

import com.example.team3final.domain.ai.rag.entity.AiRagDocumentIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * RAG 문서별 색인 hash를 조회하고 저장하는 Repository입니다.
 *
 * pgvector에는 실제 embedding chunk를 저장하고,
 * 이 Repository는 classpath 문서가 이미 색인되었는지 판단하는 메타데이터를 관리합니다.
 */
public interface AiRagDocumentIndexRepository extends JpaRepository<AiRagDocumentIndex, Long> {

    Optional<AiRagDocumentIndex> findBySource(String source);

    void deleteAllByFeature(String feature);
}
