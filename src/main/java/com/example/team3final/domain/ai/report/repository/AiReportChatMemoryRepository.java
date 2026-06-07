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

    interface ExpiredConversationKey {
        Long getAdminId();

        String getConversationId();
    }
}
