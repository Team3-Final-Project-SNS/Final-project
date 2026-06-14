package com.example.team3final.domain.ai.matching.repository;

import com.example.team3final.common.config.AiProperties;
import com.example.team3final.domain.ai.matching.dto.PostVectorSearchResultDto;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭 AI 게시글 전용 PostgreSQL + pgvector 저장소입니다.
 *
 * 원본 게시글 데이터와 시간/책임비/상태는 MySQL posts 테이블에 유지하고, 이 저장소는
 * 자연어 추천 검색을 위한 postId/title/description/embedding과 후보 필터링용 메타데이터를 관리합니다.
 *
 * 현재 title은 게시글 placeName, description은 게시글 content(한마디)를 의미합니다. 학교/상태/시간/책임비 같은
 * 정확 조건은 메타데이터 컬럼으로 먼저 거르고, MySQL에서 다시 최종 검증합니다.
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "app.ai.rag-store", name = "enabled", havingValue = "true")
public class PostVectorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final AiProperties.RagStore ragStore;
    private final String qualifiedTableName;

    public PostVectorRepository(
            @Qualifier("aiRagJdbcTemplate") JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            AiProperties aiProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.ragStore = aiProperties.getRagStore();
        this.qualifiedTableName = qualify(ragStore.getSchemaName(), ragStore.getPostTableName());
        initializeSchema();
    }

    public void upsertPost(PostVectorUpsertEvent event) {
        if (event == null || event.postId() == null || !hasText(event.title())) {
            return;
        }

        // embedding에는 장소명, 게시글 한마디, 시간대 표현만 넣어 사용자의 자연어 조건과 의미적으로 맞춥니다.
        // 학교/상태/시간/책임비/정원처럼 정확해야 하는 값은 벡터가 아니라 아래 메타데이터 컬럼으로 필터링합니다.
        String normalizedDescription = event.description() == null ? "" : event.description();
        String embeddingText = """
                제목: %s
                설명: %s
                시간대: %s
                """.formatted(event.title(), normalizedDescription, describeMealTime(event.meetAt()));
        String vector = toVectorLiteral(embeddingModel.embed(embeddingText));

        jdbcTemplate.update("""
                        INSERT INTO %s (
                            post_id, author_id, university_id, status, meet_at,
                            title, description, author_deposit, max_applicants, current_applicants,
                            place_lat, place_lng, embedding, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, NOW())
                        ON CONFLICT (post_id)
                        DO UPDATE SET
                            author_id = EXCLUDED.author_id,
                            university_id = EXCLUDED.university_id,
                            status = EXCLUDED.status,
                            meet_at = EXCLUDED.meet_at,
                            title = EXCLUDED.title,
                            description = EXCLUDED.description,
                            author_deposit = EXCLUDED.author_deposit,
                            max_applicants = EXCLUDED.max_applicants,
                            current_applicants = EXCLUDED.current_applicants,
                            place_lat = EXCLUDED.place_lat,
                            place_lng = EXCLUDED.place_lng,
                            embedding = EXCLUDED.embedding,
                            updated_at = NOW()
                        """.formatted(qualifiedTableName),
                event.postId(),
                event.authorId(),
                event.universityId(),
                event.status().name(),
                event.meetAt(),
                event.title(),
                normalizedDescription,
                event.authorDeposit(),
                event.maxApplicants(),
                event.currentApplicants(),
                event.placeLat(),
                event.placeLng(),
                vector
        );
    }

    public List<PostVectorSearchResultDto> search(
            String query,
            Long universityId,
            Long requesterId,
            int maxAuthorDeposit,
            int topK,
            double similarityThreshold
    ) {
        if (!hasText(query) || topK <= 0) {
            return List.of();
        }

        String vector = toVectorLiteral(embeddingModel.embed(query));

        // pgvector 검색은 "추천 후보 postId를 좁히는 1차 검색"입니다.
        // 최종 신청 가능 여부는 PostService가 MySQL posts/matches/users 기준으로 한 번 더 확인합니다.
        return jdbcTemplate.query("""
                        SELECT post_id,
                               title,
                               description,
                               1 - (embedding <=> ?::vector) AS similarity
                        FROM %s
                        WHERE university_id = ?
                          AND status = 'OPEN'
                          AND meet_at > NOW()
                          AND author_id <> ?
                          AND author_deposit <= ?
                          AND current_applicants < max_applicants
                          AND 1 - (embedding <=> ?::vector) >= ?
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?
                        """.formatted(qualifiedTableName),
                (rs, rowNum) -> new PostVectorSearchResultDto(
                        rs.getLong("post_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getDouble("similarity")
                ),
                vector,
                universityId,
                requesterId,
                maxAuthorDeposit,
                vector,
                similarityThreshold,
                vector,
                topK
        );
    }

    public void deletePost(Long postId) {
        if (postId == null) {
            return;
        }

        jdbcTemplate.update("DELETE FROM %s WHERE post_id = ?".formatted(qualifiedTableName), postId);
    }

    private void initializeSchema() {
        if (!ragStore.isInitializeSchema()) {
            return;
        }

        // Spring AI의 문서 RAG 테이블과 별개로, 매칭 추천 전용 게시글 벡터 테이블을 직접 관리합니다.
        // 운영에서는 initializeSchema=false로 두고 마이그레이션 도구로 같은 구조를 관리할 수 있습니다.
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        if (hasText(ragStore.getSchemaName())) {
            jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(ragStore.getSchemaName()));
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                    post_id BIGINT PRIMARY KEY,
                    author_id BIGINT NOT NULL,
                    university_id BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    meet_at TIMESTAMPTZ NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    description TEXT NOT NULL,
                    author_deposit INTEGER NOT NULL,
                    max_applicants INTEGER NOT NULL,
                    current_applicants INTEGER NOT NULL,
                    place_lat NUMERIC(10, 7) NOT NULL,
                    place_lng NUMERIC(10, 7) NOT NULL,
                    embedding vector(%d) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """.formatted(qualifiedTableName, ragStore.getDimensions()));
        addColumnIfMissing("author_id BIGINT");
        addColumnIfMissing("university_id BIGINT");
        addColumnIfMissing("status VARCHAR(20)");
        addColumnIfMissing("meet_at TIMESTAMPTZ");
        addColumnIfMissing("author_deposit INTEGER");
        addColumnIfMissing("max_applicants INTEGER");
        addColumnIfMissing("current_applicants INTEGER");
        addColumnIfMissing("place_lat NUMERIC(10, 7)");
        addColumnIfMissing("place_lng NUMERIC(10, 7)");
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS %s
                ON %s
                USING hnsw (embedding vector_cosine_ops)
                """.formatted(indexName(ragStore.getPostTableName()), qualifiedTableName));
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS %s
                ON %s (university_id, status, meet_at, author_deposit)
                """.formatted(metadataIndexName(ragStore.getPostTableName()), qualifiedTableName));
    }

    private void addColumnIfMissing(String columnDefinition) {
        jdbcTemplate.execute("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s".formatted(qualifiedTableName, columnDefinition));
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    private static String describeMealTime(LocalDateTime meetAt) {
        if (meetAt == null) {
            return "";
        }

        int hour = meetAt.getHour();
        String timeSlot;
        if (hour >= 5 && hour < 11) {
            timeSlot = "아침";
        } else if (hour >= 11 && hour < 15) {
            timeSlot = "점심";
        } else if (hour >= 15 && hour < 18) {
            timeSlot = "오후";
        } else if (hour >= 18 && hour < 22) {
            timeSlot = "저녁";
        } else {
            timeSlot = "야식";
        }

        return "%s %02d시".formatted(timeSlot, hour);
    }

    private static String qualify(String schemaName, String tableName) {
        if (hasText(schemaName)) {
            return quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        }
        return quoteIdentifier(tableName);
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String indexName(String tableName) {
        String normalized = tableName == null ? "post_vector_index" : tableName.replaceAll("[^a-zA-Z0-9_]", "_");
        return quoteIdentifier(normalized + "_embedding_idx");
    }

    private static String metadataIndexName(String tableName) {
        String normalized = tableName == null ? "post_vector_index" : tableName.replaceAll("[^a-zA-Z0-9_]", "_");
        return quoteIdentifier(normalized + "_metadata_idx");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
