package com.example.team3final.domain.chat.scheduler;

import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.enums.ChatRoomStatus;
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

    // 1분마다 실행 - 만남 완료 후 2시간 경과한 채팅방 READ_ONLY 전환
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void deactivateExpiredChatRooms() {
        LocalDateTime now = LocalDateTime.now();

        // status = ACTIVE이면서 deactivatedAt이 지난 채팅방 조회
        // (노쇼 방은 deactivatedAt = null 이라 자동 제외)
        List<ChatRoom> expiredRooms = chatRoomRepository
                .findByStatusAndDeactivatedAtBefore(ChatRoomStatus.ACTIVE, now);

        if (expiredRooms.isEmpty()) {
            return;
        }

        // 만남 완료 후 2시간 경과 → READ_ONLY 전환 (DEACTIVATED 아님)
        expiredRooms.forEach(ChatRoom::deactivateByScheduler);

        log.info("[ChatRoomScheduler] 만남 완료 채팅방 READ_ONLY 전환 - 처리 건수: {}", expiredRooms.size());
    }
}
