package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.InquiryException;
import com.example.team3final.domain.admin.inquiryAnswer.entity.InquiryAnswer;
import com.example.team3final.domain.admin.inquiryAnswer.repository.InquiryAnswerRepository;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InquiryQueryService 단위 테스트")
class InquiryQueryServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @Mock
    private InquiryAnswerRepository inquiryAnswerRepository;

    @InjectMocks
    private InquiryQueryServiceImpl inquiryQueryService;

    @Test
    @DisplayName("문의 상세 조회는 본인 문의와 답변 정보를 함께 반환한다")
    void getOneInquiry_shouldReturnInquiryWithAnswer() {
        Inquiry inquiry = inquiry(1L, 10L);
        InquiryAnswer answer = InquiryAnswer.builder()
                .inquiryId(10L)
                .adminId(100L)
                .adminName("관리자")
                .content("답변 내용")
                .build();
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));
        when(inquiryAnswerRepository.findByInquiryId(10L)).thenReturn(Optional.of(answer));

        GetOneInquiryResponseDto result = inquiryQueryService.getOneInquiry(1L, 10L);

        assertThat(result.inquiryId()).isEqualTo(10L);
        assertThat(result.answer()).isNotNull();
        assertThat(result.answer().adminName()).isEqualTo("관리자");
    }

    @Test
    @DisplayName("문의 상세 조회는 다른 사용자의 문의이면 문의 예외를 던진다")
    void getOneInquiry_shouldThrowWhenNotOwner() {
        Inquiry inquiry = inquiry(2L, 10L);
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        assertThatThrownBy(() -> inquiryQueryService.getOneInquiry(1L, 10L))
                .isInstanceOf(InquiryException.class);
    }

    @Test
    @DisplayName("내 문의 목록 조회는 사용자 ID 기준 최신순 페이지를 반환한다")
    void getAllInquiries_shouldReturnPagedInquiries() {
        PageRequest pageable = PageRequest.of(0, 10);
        Inquiry inquiry = inquiry(1L, 10L);
        when(inquiryRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(inquiry), pageable, 1));

        PageResponseDto<GetAllInquiriesResponseDto> result = inquiryQueryService.getAllInquiries(1L, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).inquiryId()).isEqualTo(10L);
        verify(inquiryRepository).findByUserIdOrderByCreatedAtDesc(1L, pageable);
    }

    private Inquiry inquiry(Long userId, Long inquiryId) {
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title("문의")
                .content("문의 내용")
                .inquiryType(InquiryType.PAYMENT)
                .build();
        ReflectionTestUtils.setField(inquiry, "id", inquiryId);
        ReflectionTestUtils.setField(inquiry, "answerStatus", InquiryAnswerStatus.PENDING);
        return inquiry;
    }
}
