package com.example.team3final.domain.ai.matching.repository;

import com.example.team3final.domain.ai.matching.entity.AiMatchingChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 매칭 AI의 구조화 응답과 추천 결과 기록을 저장하고 조회하는 Repository입니다.
 */
public interface AiMatchingChatMessageRepository extends JpaRepository<AiMatchingChatMessage, Long> {

    List<AiMatchingChatMessage> findByUserIdAndConversationIdOrderByCreatedAtAsc(
            Long userId,
            String conversationId
    );

    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
