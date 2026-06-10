package com.example.team3final.domain.ai.matching.repository;

import com.example.team3final.domain.ai.matching.entity.AiMatchingChatMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭 AI의 멀티턴 대화 메모리를 저장하고 조회하는 Repository입니다.
 */
public interface AiMatchingChatMemoryRepository extends JpaRepository<AiMatchingChatMemory, Long> {

    List<AiMatchingChatMemory> findByUserIdAndConversationIdOrderByCreatedAtDesc(
            Long userId,
            String conversationId
    );

    void deleteByUserIdAndConversationId(Long userId, String conversationId);

    @Query("""
            select m.userId as userId, m.conversationId as conversationId
            from AiMatchingChatMemory m
            group by m.userId, m.conversationId
            having max(m.createdAt) < :cutoff
            """)
    List<ExpiredConversationKey> findExpiredConversationKeys(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select m.userId as userId,
                   m.conversationId as conversationId,
                   coalesce(sum(m.tokenCount), 0) as totalTokens
            from AiMatchingChatMemory m
            group by m.userId, m.conversationId
            having max(m.createdAt) >= :cutoff
            """)
    List<ActiveConversationTokenStats> findActiveConversationTokenStats(@Param("cutoff") LocalDateTime cutoff);

    interface ExpiredConversationKey {
        Long getUserId();

        String getConversationId();
    }

    interface ActiveConversationTokenStats {
        Long getUserId();

        String getConversationId();

        Long getTotalTokens();
    }
}
