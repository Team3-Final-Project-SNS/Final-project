package com.example.team3final.domain.ai.rag.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * RAG 문서 색인 상태를 기록하는 엔티티입니다.
 *
 * 실제 embedding chunk는 PostgreSQL pgvector에 저장하고,
 * 이 엔티티는 MySQL에 source 파일 경로와 contentHash를 저장해
 * 서버 재시작 시 변경되지 않은 문서를 중복 색인하지 않도록 합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_rag_document_indexes",
        indexes = {
                @Index(name = "idx_ai_rag_document_feature", columnList = "feature"),
                @Index(name = "idx_ai_rag_document_source", columnList = "source", unique = true)
        }
)
public class AiRagDocumentIndex extends BaseTimeEntity {

    /**
     * RAG 문서 색인 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 문서를 사용하는 AI 기능 구분입니다.
     * 예: support, report처럼 기능별 RAG 문서를 나눠 관리합니다.
     */
    @Column(nullable = false, length = 30)
    private String feature;

    /**
     * 색인한 원본 문서 경로 또는 식별자입니다.
     * 같은 source를 중복 색인하지 않도록 unique로 관리합니다.
     */
    @Column(nullable = false, unique = true, length = 500)
    private String source;

    /**
     * 원본 문서 내용의 해시값입니다.
     * 파일 내용이 바뀌지 않았으면 재색인을 건너뛰기 위해 사용합니다.
     */
    @Column(nullable = false, length = 64)
    private String contentHash;

    /**
     * 해당 문서에서 생성된 chunk 개수입니다.
     * 색인 결과 확인과 RAG 문서 규모 파악에 사용합니다.
     */
    @Column(nullable = false)
    private int chunkCount;

    /**
     * 마지막으로 색인한 시각입니다.
     * 문서 변경 시점과 실제 색인 반영 시점을 비교할 때 사용합니다.
     */
    @Column(nullable = false)
    private LocalDateTime indexedAt;

    public boolean hasSameHash(String contentHash) {
        return this.contentHash.equals(contentHash);
    }

    public void updateIndex(String feature, String contentHash, int chunkCount) {
        this.feature = feature;
        this.contentHash = contentHash;
        this.chunkCount = chunkCount;
        this.indexedAt = LocalDateTime.now();
    }
}
