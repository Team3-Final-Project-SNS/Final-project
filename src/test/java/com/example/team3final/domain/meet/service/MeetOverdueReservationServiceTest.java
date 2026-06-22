package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MeetOverdueReservationService 단위 테스트")
class MeetOverdueReservationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Test
    @DisplayName("Redis 예약 갱신에 성공하면 true를 반환한다")
    void updateReservation_shouldReturnTrueWhenRedisAddSucceeds() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        MeetOverdueReservationService service = new MeetOverdueReservationService(redisTemplate);

        boolean response = service.updateReservation(
                MeetRedisZSetKeys.REMINDER_OVERDUE_HOST,
                "20",
                LocalDateTime.now().plusMinutes(10)
        );

        assertThat(response).isTrue();
        verify(zSetOperations).add(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("20"), anyDouble());
    }
}
