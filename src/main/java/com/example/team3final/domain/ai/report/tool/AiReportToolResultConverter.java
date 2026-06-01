package com.example.team3final.domain.ai.report.tool;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 신고 AI Tool 조회 결과를 LLM이 읽기 쉬운 한국어 텍스트로 변환합니다.
 *
 * record 형태의 내부 조회 결과를 프롬프트 컨텍스트에 들어갈 문자열로 바꿔
 * 신고 분석과 고위험 유저 판단 근거가 명확하게 전달되도록 합니다.
 */
public class AiReportToolResultConverter implements ToolCallResultConverter {

    /**
     * Tool 호출 결과 객체를 LLM 프롬프트에 삽입 가능한 문자열로 변환합니다.
     *
     * 신고 단건 분석 결과는 formatContext로 변환하고,
     * 고위험 유저 후보 목록은 각 후보를 formatHighRiskUser로 변환해 이어 붙입니다.
     * 알 수 없는 결과 타입이나 빈 목록은 LLM이 추측하지 않도록 명시적인 기본 문구를 반환합니다.
     */
    @Override
    public String convert(Object result, Type returnType) {
        if (result instanceof AiReportContextToolResult context) {
            return formatContext(context);
        }

        if (result instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof AiReportHighRiskUserToolResult user) {
                    sb.append(formatHighRiskUser(user)).append("\n");
                }
            }
            return sb.isEmpty() ? "고위험 후보 유저가 없습니다." : sb.toString();
        }

        return "조회 결과가 없습니다.";
    }

    /**
     * 신고 단건 분석에 필요한 Tool 결과를 사람이 읽기 쉬운 한국어 컨텍스트로 변환합니다.
     *
     * 신고 사유, 신고 상태, 신고 상세, 신고자, 대상 게시글,
     * 피신고 유저의 누적 신고 현황을 한 번에 전달하여
     * LLM이 제공된 데이터 안에서만 위험도와 처리 제안을 판단하도록 돕습니다.
     */
    private String formatContext(AiReportContextToolResult context) {
        return String.format(
                """
                신고 ID: %d
                신고 사유: %s
                신고 상태: %s
                신고 상세: %s
                신고자: %s(%d)
                대상 게시글 ID: %d
                대상 게시글 존재 여부: %s
                피신고 유저: %s(%s)
                게시글 장소: %s
                게시글 시간: %s
                게시글 내용: %s
                피신고 유저 전체 신고 수: %d
                피신고 유저 대기 신고 수: %d
                피신고 유저 채택 신고 수: %d
                """,
                context.reportId(),
                context.reportReason(),
                context.reportStatus(),
                blankToDefault(context.reportDetail()),
                blankToDefault(context.reporterNickname()),
                context.reporterId(),
                context.targetPostId(),
                context.targetPostFound() ? "존재" : "없음",
                blankToDefault(context.targetUserNickname()),
                context.targetUserId() == null ? "알 수 없음" : context.targetUserId(),
                blankToDefault(context.targetPlaceName()),
                blankToDefault(context.targetMeetAt()),
                blankToDefault(context.targetPostContent()),
                context.targetUserTotalReportCount(),
                context.targetUserPendingReportCount(),
                context.targetUserAcceptedReportCount()
        );
    }

    /**
     * 고위험 유저 후보 Tool 결과를 LLM이 비교하기 쉬운 텍스트로 변환합니다.
     *
     * 전체 신고 수, 대기 신고 수, 채택 신고 수, 기각 신고 수를 함께 제공해
     * LLM이 반복 신고와 실제 채택 이력을 구분해서 위험도를 설명할 수 있게 합니다.
     */
    private String formatHighRiskUser(AiReportHighRiskUserToolResult user) {
        return String.format(
                """
                유저 ID: %d
                닉네임: %s
                전체 신고 수: %d
                대기 신고 수: %d
                채택 신고 수: %d
                기각 신고 수: %d
                관련 신고 ID: %s
                신고 사유 요약: %s
                """,
                user.userId(),
                blankToDefault(user.nickname()),
                user.totalReportCount(),
                user.pendingReportCount(),
                user.acceptedReportCount(),
                user.rejectedReportCount(),
                user.relatedReportIds(),
                blankToDefault(user.reasonSummary())
        );
    }

    /**
     * null 또는 빈 문자열을 "정보 없음"으로 치환합니다.
     *
     * Tool 결과 일부가 비어 있어도 LLM 프롬프트에 null/blank가 그대로 들어가지 않게 하여
     * 불필요한 추측과 환각 가능성을 줄입니다.
     */
    private String blankToDefault(String value) {
        return value == null || value.isBlank() ? "정보 없음" : value;
    }
}
