package com.example.team3final.domain.chat.scheduler;

import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomScheduler {

    private final ChatRoomRepository chatRoomRepository;

    // 1분마다 실행 - 비활성화 예정 시각이 지난 활성 채팅방 자동 비활성화
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void deactivateExpiredChatRooms() {
        LocalDateTime now = LocalDateTime.now();

        // 비활성화 예정 시각이 지난 활성 채팅방 조회
        List<ChatRoom> expiredRooms = chatRoomRepository
                .findByIsActiveTrueAndDeactivatedAtBefore(now);

        if (expiredRooms.isEmpty()) {
            return;
        }

        expiredRooms.forEach(ChatRoom::deactivate);

        log.info("[Scheduler] 채팅방 자동 비활성화 - 처리 건수: {}", expiredRooms.size());
    }
}
