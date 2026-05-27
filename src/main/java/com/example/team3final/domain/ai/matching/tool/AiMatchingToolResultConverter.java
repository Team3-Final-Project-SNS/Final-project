package com.example.team3final.domain.ai.matching.tool;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;
import java.util.List;


/**
 * 매칭 AI Tool 조회 결과를 LLM이 읽기 쉬운 문자열로 변환하는 Converter입니다.
 *
 * AiMatchingTool이 반환한 AiMatchingPostToolResult 또는 후보 목록을
 * 장소, 시간, 책임비, 신청 가능 여부, 포인트 충족 여부가 포함된 문자열로 변환합니다.
 *
 * 변환된 문자열은 프롬프트의 candidatePosts 영역에 주입되어,
 * LLM이 제공된 후보 안에서만 추천하고 신청 가능 여부를 잘못 판단하지 않도록 돕습니다.
 */
public class AiMatchingToolResultConverter implements ToolCallResultConverter {


    /**
     * Tool 실행 결과를 문자열로 변환합니다.
     *
     * 단일 AiMatchingPostToolResult 또는 List<AiMatchingPostToolResult>를 지원합니다.
     */
    @Override
    public String convert(Object result, Type returnType) {
        if (result instanceof AiMatchingPostToolResult post) {
            return formatPost(post);
        }

        if (result instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();

            for (Object item : list) {
                if (item instanceof AiMatchingPostToolResult post) {
                    sb.append(formatPost(post)).append("\n");
                }
            }

            return sb.toString();
        }

        return "조회 결과가 없습니다.";
    }

    /**
     * 하나의 모집글 후보를 LLM 프롬프트에 넣을 수 있는 문자열로 변환합니다.
     */
    private String formatPost(AiMatchingPostToolResult post) {
        return String.format(
                """
                게시글 ID: %d
                장소: %s
                시간: %s
                책임비: %dP
                내용: %s
                신청 가능 여부: %s
                포인트 충분 여부: %s
                신청 불가 사유: %s
                """,
                post.postId(),
                post.placeName(),
                post.meetAt(),
                post.deposit(),
                post.content(),
                post.applicationAvailable() ? "가능" : "불가능",
                post.pointAffordable() ? "충분" : "부족",
                post.unavailableReason() == null ? "없음" : post.unavailableReason()
        );
    }
}