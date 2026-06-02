package com.example.team3final.domain.ai.rag.config;

import com.example.team3final.common.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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

    @Bean
    public DataSource ragDataSource() {
        AiProperties.RagStore ragStore = aiProperties.getRagStore();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(ragStore.getDriverClassName());
        dataSource.setUrl(ragStore.getUrl());
        dataSource.setUsername(ragStore.getUsername());
        dataSource.setPassword(ragStore.getPassword());
        return dataSource;
    }

    @Bean
    public VectorStore vectorStore(DataSource ragDataSource, EmbeddingModel embeddingModel) {
        AiProperties.RagStore ragStore = aiProperties.getRagStore();

        return PgVectorStore.builder(new JdbcTemplate(ragDataSource), embeddingModel)
                .schemaName(ragStore.getSchemaName())
                .vectorTableName(ragStore.getTableName())
                .dimensions(ragStore.getDimensions())
                .initializeSchema(ragStore.isInitializeSchema())
                .distanceType(PgVectorStore.PgDistanceType.valueOf(ragStore.getDistanceType()))
                .indexType(PgVectorStore.PgIndexType.valueOf(ragStore.getIndexType()))
                .build();
    }
}
