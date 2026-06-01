package com.example.team3final.domain.ai.report.tool;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 신고 AI가 판단 근거를 얻기 위해 호출하는 Spring AI Tool 모음입니다.
 *
 * 신고 원문, 대상 게시글, 신고자/피신고자 정보, 누적 신고 현황을 조회해
 * LLM이 외부 추측 없이 서비스 내부 데이터만으로 판단하도록 돕습니다.
 */
@Component
@RequiredArgsConstructor
public class AiReportTool {

    private static final int MAX_REPORT_SCAN_SIZE = 100;

    private final ReportService reportService;
    private final PostService postService;
    private final UserService userService;

    /**
     * 특정 신고 건의 분석에 필요한 전체 맥락을 조회합니다.
     *
     * LLM이 신고 원문만 보고 추측하지 않도록 신고자 정보, 대상 게시글 정보,
     * 피신고 유저 정보, 누적 신고 수를 하나의 Tool 결과로 제공합니다.
     *
     * 대상 게시글을 찾지 못하는 경우에도 신고 자체의 정보는 반환하여
     * AI가 "대상 게시글 확인 불가" 상태를 근거로 보수적으로 판단할 수 있게 합니다.
     */
    @Tool(
            description = "신고 ID를 기준으로 신고 내용, 신고 대상 게시글, 피신고 유저의 누적 신고 정보를 조회합니다.",
            resultConverter = AiReportToolResultConverter.class
    )
    public AiReportContextToolResult getReportContext(
            @ToolParam(description = "분석할 신고 ID", required = true)
            Long reportId
    ) {
        Report report = reportService.getReportById(reportId);

        UserInfoDto reporter = userService.getUserInfo(report.getReporterId());

        Post targetPost;
        try {
            targetPost = postService.getPostById(report.getTargetId());
        } catch (Exception e) {
            return new AiReportContextToolResult(
                    report.getId(),
                    report.getReason(),
                    report.getStatus(),
                    report.getDetail(),
                    report.getReporterId(),
                    reporter.nickname(),
                    report.getTargetId(),
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0
            );
        }

        UserInfoDto targetUser = userService.getUserInfo(targetPost.getAuthorId());
        UserReportCounts counts = countReportsByTargetUser(targetPost.getAuthorId());

        return new AiReportContextToolResult(
                report.getId(),
                report.getReason(),
                report.getStatus(),
                report.getDetail(),
                report.getReporterId(),
                reporter.nickname(),
                targetPost.getId(),
                true,
                targetPost.getAuthorId(),
                targetUser.nickname(),
                targetPost.getContent(),
                targetPost.getPlaceName(),
                targetPost.getMeetAt().toString(),
                counts.totalCount(),
                counts.pendingCount(),
                counts.acceptedCount()
        );
    }

    /**
     * 최근 신고 데이터를 기반으로 고위험 유저 후보를 조회합니다.
     *
     * 최근 관리자 신고 목록을 제한된 범위로 스캔하고, 신고 대상 게시글의 작성자를 기준으로
     * 유저별 신고 수, 미처리 신고 수, 채택 신고 수를 집계합니다.
     *
     * LLM은 이 Tool 결과를 바탕으로 위험도와 관리자 권장 조치를 자연어로 정리하며,
     * 실제 계정 정지나 제재 처리는 별도의 관리자 API에서 수행합니다.
     */
    @Tool(
            description = "최근 신고 내역을 기준으로 반복 신고 또는 채택 신고가 많은 고위험 유저 후보를 조회합니다.",
            resultConverter = AiReportToolResultConverter.class
    )
    public List<AiReportHighRiskUserToolResult> findHighRiskUserCandidates(
            @ToolParam(description = "조회할 후보 수. 기본값은 5", required = false)
            Integer limit
    ) {
        int resultLimit = limit == null || limit <= 0 ? 5 : Math.min(limit, 20);

        List<Report> reports = reportService
                .getReportsForAdmin(null, PageRequest.of(0, MAX_REPORT_SCAN_SIZE))
                .getContent();

        Map<Long, MutableUserRisk> riskMap = new HashMap<>();

        for (Report report : reports) {
            Post post;
            try {
                post = postService.getPostById(report.getTargetId());
            } catch (Exception e) {
                continue;
            }

            Long targetUserId = post.getAuthorId();
            MutableUserRisk risk = riskMap.computeIfAbsent(targetUserId, MutableUserRisk::new);
            risk.add(report);
        }

        return riskMap.values()
                .stream()
                .sorted(Comparator
                        .comparingInt(MutableUserRisk::score)
                        .reversed()
                        .thenComparing(MutableUserRisk::userId))
                .limit(resultLimit)
                .map(this::toResult)
                .toList();
    }

    /**
     * 내부 집계 객체를 LLM에 전달 가능한 Tool 결과 DTO로 변환합니다.
     *
     * 유저 닉네임과 신고 사유 요약을 함께 포함해
     * AI가 관리자에게 읽기 쉬운 고위험 후보 설명을 생성할 수 있게 합니다.
     */
    private AiReportHighRiskUserToolResult toResult(MutableUserRisk risk) {
        UserInfoDto userInfo = userService.getUserInfo(risk.userId());

        return new AiReportHighRiskUserToolResult(
                risk.userId(),
                userInfo.nickname(),
                risk.totalCount(),
                risk.pendingCount(),
                risk.acceptedCount(),
                risk.rejectedCount(),
                risk.reportIds(),
                risk.reasonSummary()
        );
    }

    /**
     * 특정 유저가 신고 대상이 된 누적 현황을 계산합니다.
     *
     * 신고 엔티티의 targetId는 게시글 ID이므로, 게시글을 조회해 작성자 ID를 확인한 뒤
     * 해당 작성자에게 쌓인 전체/미처리/채택 신고 수를 집계합니다.
     */
    private UserReportCounts countReportsByTargetUser(Long targetUserId) {
        List<Report> reports = reportService
                .getReportsForAdmin(null, PageRequest.of(0, MAX_REPORT_SCAN_SIZE))
                .getContent();

        int totalCount = 0;
        int pendingCount = 0;
        int acceptedCount = 0;

        for (Report report : reports) {
            Post post;
            try {
                post = postService.getPostById(report.getTargetId());
            } catch (Exception e) {
                continue;
            }

            if (!post.getAuthorId().equals(targetUserId)) {
                continue;
            }

            totalCount++;
            if (report.getStatus() == ReportStatus.PENDING) {
                pendingCount++;
            }
            if (report.getStatus() == ReportStatus.ACCEPTED) {
                acceptedCount++;
            }
        }

        return new UserReportCounts(totalCount, pendingCount, acceptedCount);
    }

    /**
     * 특정 유저의 신고 누적 수를 표현하는 값 객체입니다.
     *
     * 단건 신고 분석에서 피신고 유저의 반복 신고 여부를 판단하는 근거로 사용합니다.
     */
    private record UserReportCounts(
            int totalCount,
            int pendingCount,
            int acceptedCount
    ) {
    }

    /**
     * 고위험 유저 후보 선정을 위한 내부 가변 집계 객체입니다.
     *
     * 최근 신고 목록을 순회하면서 유저별 신고 수, 처리 상태별 건수,
     * 신고 사유 빈도와 관련 신고 ID를 누적합니다.
     */
    private static class MutableUserRisk {
        private final Long userId;
        private final List<Long> reportIds = new ArrayList<>();
        private final Map<ReportReason, Integer> reasonCounts = new HashMap<>();
        private int totalCount;
        private int pendingCount;
        private int acceptedCount;
        private int rejectedCount;

        private MutableUserRisk(Long userId) {
            this.userId = userId;
        }

        /**
         * 신고 1건을 현재 유저의 위험도 집계에 반영합니다.
         *
         * 신고 사유 빈도와 처리 상태별 건수를 함께 누적해
         * 이후 score와 reasonSummary 계산에 사용합니다.
         */
        private void add(Report report) {
            totalCount++;
            reportIds.add(report.getId());
            reasonCounts.merge(report.getReason(), 1, Integer::sum);

            if (report.getStatus() == ReportStatus.PENDING) {
                pendingCount++;
            } else if (report.getStatus() == ReportStatus.ACCEPTED) {
                acceptedCount++;
            } else if (report.getStatus() == ReportStatus.REJECTED) {
                rejectedCount++;
            }
        }

        private Long userId() {
            return userId;
        }

        private int totalCount() {
            return totalCount;
        }

        private int pendingCount() {
            return pendingCount;
        }

        private int acceptedCount() {
            return acceptedCount;
        }

        private int rejectedCount() {
            return rejectedCount;
        }

        private List<Long> reportIds() {
            return List.copyOf(reportIds);
        }

        /**
         * 고위험 후보 정렬에 사용할 단순 위험 점수를 계산합니다.
         *
         * 채택 신고는 실제 위반 가능성이 높으므로 가장 큰 가중치를 부여하고,
         * 미처리 신고는 검토 필요성이 있으므로 중간 가중치를 부여합니다.
         * 기각 신고는 오신고 가능성을 반영해 점수에서 차감합니다.
         */
        private int score() {
            return acceptedCount * 4 + pendingCount * 2 + totalCount - rejectedCount;
        }

        /**
         * 유저에게 누적된 신고 사유를 사람이 읽을 수 있는 문장으로 요약합니다.
         *
         * 예: SPAM 2건, ABUSE 1건
         */
        private String reasonSummary() {
            if (reasonCounts.isEmpty()) {
                return "신고 사유 없음";
            }

            StringBuilder sb = new StringBuilder();
            reasonCounts.forEach((reason, count) ->
                    sb.append(reason).append(" ").append(count).append("건, ")
            );

            return sb.substring(0, sb.length() - 2);
        }
    }
}
