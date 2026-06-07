package com.example.team3final.domain.ai.report.repository;

import com.example.team3final.domain.ai.report.entity.AiReportChatMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 신고 AI의 멀티턴 대화 메모리를 저장하고 조회하는 Repository입니다.
 */
public interface AiReportChatMemoryRepository extends JpaRepository<AiReportChatMemory, Long> {

    List<AiReportChatMemory> findByAdminIdAndConversationIdOrderByCreatedAtDesc(
            Long adminId,
            String conversationId
    );

    void deleteByAdminIdAndConversationId(Long adminId, String conversationId);

    @Query("""
            select m.adminId as adminId, m.conversationId as conversationId
            from AiReportChatMemory m
            group by m.adminId, m.conversationId
            having max(m.createdAt) < :cutoff
            """)
    List<ExpiredConversationKey> findExpiredConversationKeys(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select m.conversationId as conversationId,
                   count(m.id) as messageCount,
                   coalesce(sum(m.tokenCount), 0) as estimatedTokenTotal,
                   max(m.createdAt) as lastMessageAt
            from AiReportChatMemory m
            where m.adminId = :adminId
            group by m.conversationId
            order by max(m.createdAt) desc
            """)
    List<SessionTokenStats> findSessionTokenStatsByAdminId(@Param("adminId") Long adminId);

    interface ExpiredConversationKey {
        Long getAdminId();

        String getConversationId();
    }

    interface SessionTokenStats {
        String getConversationId();

        Long getMessageCount();

        Long getEstimatedTokenTotal();

        LocalDateTime getLastMessageAt();
    }
}
