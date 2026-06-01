package com.example.team3final.domain.ai.support.repository;

import com.example.team3final.domain.ai.support.entity.AiSupportChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 고객센터 AI 대화 메시지를 저장하고 조회하는 Repository입니다.
 *
 * 같은 conversationId의 최근 메시지를 읽어 멀티턴 대화 맥락을 구성할 때 사용합니다.
 */
public interface AiSupportChatMessageRepository extends JpaRepository<AiSupportChatMessage, Long> {

    List<AiSupportChatMessage> findTop10ByUserIdAndConversationIdOrderByCreatedAtDesc(
            Long userId,
            String conversationId
    );
}
