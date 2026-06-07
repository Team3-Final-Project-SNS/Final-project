package com.example.team3final.domain.ai.support.repository;

import com.example.team3final.domain.ai.support.entity.AiSupportChatMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 고객센터 AI의 멀티턴 대화 메모리를 저장하고 조회하는 Repository입니다.
 */
public interface AiSupportChatMemoryRepository extends JpaRepository<AiSupportChatMemory, Long> {

    List<AiSupportChatMemory> findByUserIdAndConversationIdOrderByCreatedAtDesc(
            Long userId,
            String conversationId
    );

    void deleteByUserIdAndConversationId(Long userId, String conversationId);

    @Query("""
            select m.userId as userId, m.conversationId as conversationId
            from AiSupportChatMemory m
            group by m.userId, m.conversationId
            having max(m.createdAt) < :cutoff
            """)
    List<ExpiredConversationKey> findExpiredConversationKeys(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select m.conversationId as conversationId,
                   count(m.id) as messageCount,
                   coalesce(sum(m.tokenCount), 0) as estimatedTokenTotal,
                   max(m.createdAt) as lastMessageAt
            from AiSupportChatMemory m
            where m.userId = :userId
            group by m.conversationId
            order by max(m.createdAt) desc
            """)
    List<SessionTokenStats> findSessionTokenStatsByUserId(@Param("userId") Long userId);

    interface ExpiredConversationKey {
        Long getUserId();

        String getConversationId();
    }

    interface SessionTokenStats {
        String getConversationId();

        Long getMessageCount();

        Long getEstimatedTokenTotal();

        LocalDateTime getLastMessageAt();
    }
}
