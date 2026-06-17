package com.example.team3final.domain.ai.rag.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.example.team3final.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * RAG 전용 PostgreSQL + pgvector VectorStore 설정입니다.
 *
 * 서비스 원본 DB는 MySQL을 계속 사용하고, 벡터 검색 저장소만 별도 PostgreSQL로 분리합니다.
 * Spring AI pgvector 자동 설정이 primary datasource(MySQL)를 잡지 않도록,
 * app.ai.rag-store.enabled=true일 때만 별도 DataSource와 VectorStore Bean을 직접 생성합니다.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai.rag-store", name = "enabled", havingValue = "true")
public class AiRagVectorStoreConfig {

    private final AiProperties aiProperties;

    @Bean(defaultCandidate = false)
    public DataSource aiRagDataSource() {
        // RAG/pgvector는 메인 MySQL이 아니라 별도 PostgreSQL을 사용합니다.
        // defaultCandidate=false로 두어 Spring Boot가 이 DataSource를 기본 JPA DataSource로 오인하지 않게 합니다.
        return createRagDataSource(aiProperties.getRagStore());
    }

    @Bean
    public JdbcTemplate aiRagJdbcTemplate(@Qualifier("aiRagDataSource") DataSource aiRagDataSource) {
        // 문서 RAG와 매칭 게시글 벡터 검색이 같은 RAG 전용 커넥션 풀을 재사용하도록 JdbcTemplate을 분리합니다.
        return new JdbcTemplate(aiRagDataSource);
    }

    @Bean
    public VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Qualifier("aiRagJdbcTemplate") JdbcTemplate aiRagJdbcTemplate
    ) {
        AiProperties.RagStore ragStore = aiProperties.getRagStore();

        return PgVectorStore.builder(aiRagJdbcTemplate, embeddingModel)
                .schemaName(ragStore.getSchemaName())
                .vectorTableName(ragStore.getTableName())
                .dimensions(ragStore.getDimensions())
                .initializeSchema(ragStore.isInitializeSchema())
                .distanceType(PgVectorStore.PgDistanceType.valueOf(ragStore.getDistanceType()))
                .indexType(PgVectorStore.PgIndexType.valueOf(ragStore.getIndexType()))
                .build();
    }

    private HikariDataSource createRagDataSource(AiProperties.RagStore ragStore) {
        // DriverManagerDataSource는 요청마다 새 연결을 만들 수 있어 운영 부하에 취약합니다.
        // HikariCP를 사용해 RAG 색인/검색 연결을 풀링하고, .env/application.yml에서 풀 설정을 조정합니다.
        HikariConfig config = new HikariConfig();
        config.setPoolName("ai-rag-postgres-pool");
        config.setDriverClassName(ragStore.getDriverClassName());
        config.setJdbcUrl(ragStore.getUrl());
        config.setUsername(ragStore.getUsername());
        config.setPassword(ragStore.getPassword());
        config.setMaximumPoolSize(ragStore.getMaximumPoolSize());
        config.setMinimumIdle(ragStore.getMinimumIdle());
        config.setConnectionTimeout(ragStore.getConnectionTimeoutMs());
        config.setIdleTimeout(ragStore.getIdleTimeoutMs());
        config.setMaxLifetime(ragStore.getMaxLifetimeMs());
        return new HikariDataSource(config);
    }
}
