package com.example.team3final.domain.ai.matching.tool;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;
import java.util.List;

public class AiMatchingToolResultConverter implements ToolCallResultConverter {

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