package com.example.team3final.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml / application-local.yml 의 app.ai.* 설정을 Java 객체로 매핑하는 클래스입니다.
 *
 * spring.ai.openai.* 설정은 Spring AI 자동 설정이 직접 사용하고,
 * app.ai.* 설정은 한끼팟 서비스 코드가 기능별 모델 옵션, 토큰 제한,
 * RAG 검색 기준, 메모리 윈도우 크기 등을 직접 사용하기 위해 정의합니다.
 *
 */

@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled = true;
    private long timeoutMs = 5000;

    private ChatFeature matching = new ChatFeature();
    private ChatFeature support = new ChatFeature();
    private ChatFeature report = new ChatFeature();
    private ModerationFeature moderation = new ModerationFeature();
    private Metrics metrics = new Metrics();
    private Prompt prompt = new Prompt();
    private RagStore ragStore = new RagStore();


    @Getter
    @Setter
    public static class Feature {
        private String model;
        private Integer maxTokens;
        private Double temperature;
    }

    @Getter
    @Setter
    public static class ChatFeature extends Feature {
        private Integer memoryWindowSize = 10;
        private boolean streamEnabled = true;
        private Rag rag = new Rag();
    }

    @Getter
    @Setter
    public static class ModerationFeature extends Feature {
        private boolean ruleFirst = true;
        private boolean aiCheckEnabled = true;
    }

    @Getter
    @Setter
    public static class Rag {
        private Integer topK = 5;
        private Double similarityThreshold = 0.65;
        private Double menuSimilarityThreshold = 0.35;
        private Double atmosphereSimilarityThreshold = 0.40;
    }

    @Getter
    @Setter
    public static class Metrics {
        private boolean enabled = true;
    }



    @Getter
    @Setter
    public static class Prompt {
        private String basePath = "./prompts";
        private boolean fallbackToClasspath = true;
    }



    @Getter
    @Setter
    public static class RagStore {
        private boolean enabled;
        private String driverClassName;
        private String url;
        private String username;
        private String password;
        private String schemaName;
        private String tableName;
        // 문서 RAG 테이블과 분리된 매칭 AI 게시글 추천 전용 pgvector 테이블명입니다.
        private String postTableName;
        private int dimensions;
        private boolean initializeSchema;
        private String distanceType;
        private String indexType;
        // RAG 전용 PostgreSQL은 메인 MySQL과 별도 커넥션 풀로 관리합니다.
        // 운영 환경에서 pgvector 색인/검색 부하에 맞춰 풀 크기와 타임아웃을 조정하기 위한 설정입니다.
        private int maximumPoolSize = 5;
        private int minimumIdle = 1;
        private long connectionTimeoutMs = 30000;
        private long idleTimeoutMs = 30000;
        private long maxLifetimeMs = 1800000;
    }
}
