package com.example.team3final.domain.meet.service.support;

import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("만남 연장 보조 컴포넌트 단위 테스트")
class MeetExtensionSupportTest {

    @Mock
    private MeetVerificationRepository meetVerificationRepository;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private MeetExtensionSupport meetExtensionSupport;

    @Test
    @DisplayName("연장 요청 타임아웃을 Redis ZSet에 예약한다")
    void reserveExtensionTimeout_shouldAddRedisReservation() {
        MeetVerification meetVerification = MeetVerification.createPending(10L);
        ReflectionTestUtils.setField(meetVerification, "id", 100L);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        meetExtensionSupport.reserveExtensionTimeout(meetVerification);

        verify(zSetOperations).add(anyString(), org.mockito.ArgumentMatchers.eq("100"), anyDouble());
    }

    @Test
    @DisplayName("활성 만남 인증 목록이 비어 있으면 연장 요청 가능 검증에 실패한다")
    void validateGroupExtensionRequestable_shouldThrowWhenEmpty() {
        assertThatThrownBy(() -> meetExtensionSupport.validateGroupExtensionRequestable(List.of()))
                .isInstanceOf(MeetException.class);
    }
}
