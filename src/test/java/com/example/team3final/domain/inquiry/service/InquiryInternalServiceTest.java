package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.exception.InquiryException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InquiryInternal 단위 테스트")
class InquiryInternalServiceTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @InjectMocks
    private InquiryInternalServiceImpl inquiryInternalService;

    @Test
    @DisplayName("문의 ID로 내부 조회하면 문의를 반환한다")
    void getInquiryById_shouldReturnInquiry() {
        Inquiry inquiry = inquiry();
        when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

        Inquiry response = inquiryInternalService.getInquiryById(10L);

        assertThat(response).isSameAs(inquiry);
    }

    @Test
    @DisplayName("문의가 없으면 내부 조회에 실패한다")
    void getInquiryById_shouldThrowWhenInquiryNotFound() {
        when(inquiryRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inquiryInternalService.getInquiryById(10L))
                .isInstanceOf(InquiryException.class);
    }

    @Test
    @DisplayName("관리자 문의 목록 조회는 상태와 유형 필터를 저장소에 위임한다")
    void getInquiriesForAdmin_shouldDelegateWithFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(inquiryRepository.findAllByStatusAndType(InquiryAnswerStatus.PENDING, InquiryType.OTHER, pageable))
                .thenReturn(Page.empty(pageable));

        inquiryInternalService.getInquiriesForAdmin(InquiryAnswerStatus.PENDING, InquiryType.OTHER, pageable);

        verify(inquiryRepository).findAllByStatusAndType(InquiryAnswerStatus.PENDING, InquiryType.OTHER, pageable);
    }

    private Inquiry inquiry() {
        return Inquiry.builder()
                .userId(1L)
                .title("문의")
                .content("내용")
                .inquiryType(InquiryType.OTHER)
                .build();
    }
}
