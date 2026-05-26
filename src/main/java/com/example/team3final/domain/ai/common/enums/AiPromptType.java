package com.example.team3final.domain.ai.common.enums;

/**
 * AI 기능별 프롬프트 템플릿 파일 경로를 관리하는 enum.
 *
 * 프롬프트 본문을 Java 코드에 직접 하드코딩하지 않고,
 * src/main/resources/prompts/*.st 파일로 분리하기 위해 사용.
 *
 * 각 enum 값은 하나의 AI 기능과 하나의 프롬프트 템플릿 파일을 매핑.
 *
 * 예:
 * MATCHING_CHAT
 * -> src/main/resources/prompts/matching-chat.st
 *
 * AiPromptFileService는 이 enum의 path 값을 이용해 classpath 리소스를 읽고,
 * Spring AI PromptTemplate 방식으로 변수 치환 후 ChatClient에 전달.
 */


public enum AiPromptType {
    MATCHING_CHAT("prompts/matching-chat.st"),
    SUPPORT_CHAT("prompts/support-chat.st"),
    REPORT_SUMMARY("prompts/report-summary.st"),
    CHAT_MODERATION("prompts/chat-moderation.st");

    private final String path;

    AiPromptType(String path) {
        this.path = path;
    }


    /**
     * 프롬프트 템플릿 파일 경로를 반환.
     *
     * @return classpath 기준 프롬프트 파일 경로
     */
    public String getPath() {
        return path;
    }
}