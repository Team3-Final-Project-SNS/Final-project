package com.example.team3final.domain.chat.scheduler;

import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import com.example.team3final.domain.chat.util.ChatRedisZSetKeys;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomScheduler {

    private final StringRedisTemplate redisTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final MatchInternalService matchInternalService;
    private final NotificationPublisher notificationPublisher;
    private final DefaultRedisScript<List<String>> popReadyItemsScript; // RedisConfig Bean 주입

    // 한국 시간대 오프셋 — Unix Timestamp 변환 시 KST(UTC+9) 기준 적용
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // 1분마다 실행 - 만남 완료 후 2시간 경과한 채팅방 READ_ONLY 전환
    // fixedDelay: 이전 실행 완료 후 1분 뒤 실행 (동시 실행 방지)
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void deactivateExpiredChatRooms() {

        // 현재 시각 Unix Timestamp — ZSet score 비교에 사용
        long nowScore = LocalDateTime.now().toEpochSecond(KST);

        // Lua Script로 원자적 처리 (조회 + 삭제 동시에)
        // → 서버 여러 대여도 중복 처리 없음
        List<String> chatRoomIds = redisTemplate.execute(
                popReadyItemsScript,
                List.of(ChatRedisZSetKeys.ROOM_DEACTIVATE), // KEYS[1]
                String.valueOf(nowScore)                    // ARGV[1]
        );

        if (chatRoomIds.isEmpty()) {
            return;
        }

        log.info("[ChatRoomScheduler] 만남 완료 채팅방 READ_ONLY 전환 - 처리 건수: {}", chatRoomIds.size());

        for (String idStr : chatRoomIds) {
            Long chatRoomId = Long.parseLong(idStr);

            // chatRoomId로 ChatRoom 조회 후 READ_ONLY 전환
            chatRoomRepository.findById(chatRoomId).ifPresent(chatRoom -> {

                // 이미 READ_ONLY/DEACTIVATED 된 건 스킵 (중복 처리 방지)
                if (!chatRoom.isActive()) {
                    return;
                }

                // ACTIVE → READ_ONLY 전환 (만남 완료 2시간 경과)
                chatRoom.deactivateByScheduler();

                // 만남 완료 후 신청자에게 후기 작성 유도 알림 발송
                // 단체 만남일 수 있으므로 같은 postId의 COMPLETED 매칭 신청자 전체에게 발송
                List<Match> completedMatches = matchInternalService.getCompletedMatchesByPostId(chatRoom.getPostId());

                completedMatches.forEach(match ->
                        // 9. 만남 완료 / 후기 작성 유도 알림 - 신청자에게
                        notificationPublisher.sendMeetCompleted(match.getApplicantId(), match.getId()));
            });
        }
    }
}