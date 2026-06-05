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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String feature;

    @Column(nullable = false, unique = true, length = 500)
    private String source;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false)
    private int chunkCount;

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
