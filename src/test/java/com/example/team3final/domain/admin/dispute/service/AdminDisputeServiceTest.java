package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.service.DisputeInternalService;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchNoShowService;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.domain.meet.service.MeetVerificationNoShowService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 이의제기 서비스 단위 테스트")
class AdminDisputeServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private DisputeInternalService disputeInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private MatchInternalService matchInternalService;

    @Mock
    private MatchNoShowService matchNoShowService;

    @Mock
    private MeetVerificationInternalService meetVerificationInternalService;

    @Mock
    private MeetVerificationNoShowService meetVerificationNoShowService;

    @Mock
    private ChatInternalService chatInternalService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private AdminDisputeServiceImpl adminDisputeService;

    @Test
    @DisplayName("관리자가 이의제기 목록을 조회하면 이의제기 내부 서비스 결과를 페이지 응답으로 반환한다")
    void getDisputes_shouldReturnPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(disputeInternalService.getDisputesForAdmin(DisputeStatus.SUBMITTED, pageable)).thenReturn(Page.empty(pageable));

        PageResponseDto<?> response = adminDisputeService.getDisputes(1L, DisputeStatus.SUBMITTED, pageable);

        assertThat(response.totalElements()).isZero();
        verify(disputeInternalService).getDisputesForAdmin(DisputeStatus.SUBMITTED, pageable);
    }

    @Test
    @DisplayName("관리자가 없으면 이의제기 목록 조회에 실패한다")
    void getDisputes_shouldThrowWhenAdminNotFound() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminDisputeService.getDisputes(1L, null, pageable))
                .isInstanceOf(AdminException.class);
    }

    private Admin admin() {
        return Admin.createAdmin("admin@test.com", "encoded", "관리자", AdminRole.SUPER_ADMIN);
    }
}
