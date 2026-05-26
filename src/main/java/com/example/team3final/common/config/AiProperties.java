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
    private Feature report = new Feature();
    private ModerationFeature moderation = new ModerationFeature();
    private Metrics metrics = new Metrics();

    @Getter
    @Setter
    public static class Feature {
        private String model = "gpt-4o-mini";
        private Integer maxTokens = 700;
        private Double temperature = 0.3;
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
    }

    @Getter
    @Setter
    public static class Metrics {
        private boolean enabled = true;
    }
}