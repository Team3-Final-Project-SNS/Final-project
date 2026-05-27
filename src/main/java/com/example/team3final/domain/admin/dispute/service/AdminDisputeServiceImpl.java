package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputesResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDisputeServiceImpl implements AdminDisputeService {

    private final AdminRepository adminRepository;
    private final DisputeService disputeService;
    private final UserService userService;
    private final MatchService matchService;
    private final MeetVerificationService meetVerificationService;
    private final ChatService chatService;

    // 이의제기 상세 조회 API
    @Override
    @Transactional // SUBMITTED -> UNDER_REVIEW 상태 변경이 일어나므로, 쓰기 적용
    public GetAdminDisputeResponseDto getDispute(Long adminId, Long disputeId) {

        // 어드민 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 이의 제기 조회
        Dispute dispute = disputeService.getDisputeById(disputeId);

        // SUBMITTED 상태면 UNDER_REVIEW로 자동 전환
        if (dispute.getStatus() == DisputeStatus.SUBMITTED) {
            dispute.startReview(adminId);
        }

        // submitterId를 nickname으로 조회
        String applicantNickname = userService.getUserInfo(dispute.getSubmitterId()).nickname();

        // matchId는 postId로 조회
        Long postId = matchService.getMatchInfo(dispute.getMatchId()).postId();

        // matchId는 MeetVerification으로 조회
        MeetVerification meetVerification = meetVerificationService.getByMatchId(dispute.getMatchId());

        // postId -> chatRoomId 조회 후 채팅 내역 조회
        Long chatRoomId = chatService.getChatRoomIdByPostId(postId);
        List<ChatMessageResponseDto> messages =
                chatRoomId != null ? chatService.getChatMessagesForAdmin(chatRoomId) : List.of();

        // ChatMessageResponseDto -> GetAdminDisputeResponseDto에 있는 ChatMessage로 변환
        List<GetAdminDisputeResponseDto.ChatMessage> chatMessages = messages.stream()
                .map(m -> GetAdminDisputeResponseDto.ChatMessage.of(
                        m.senderId(),
                        m.senderNickname(),
                        m.content(),
                        m.createdAt()
                ))
                .toList();

        // 최종 응답 DTO
        return GetAdminDisputeResponseDto.of(
                dispute.getId(),
                dispute.getMatchId(),
                applicantNickname,
                dispute.getReason(),
                dispute.getStatus(),
                meetVerification.getStatus(),
                meetVerification.getAuthorPlaceVerifiedAt(),
                meetVerification.getApplicantPlaceVerifiedAt(),
                dispute.getSubmittedAt(),
                chatMessages
        );
    }

    @Override
    public PageResponseDto<GetAdminDisputesResponseDto> getDisputes(Long adminId, DisputeStatus status, Pageable pageable) {

        // 어드민 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // status null이면 전체 조회, 있으면 해당 status만 필터링
        Page<Dispute> disputes = disputeService.getDisputesForAdmin(status, pageable);

        // submitterId 목록 한 번에 추출 (N+1 방지)
        List<Long> submitterIds = disputes.getContent().stream()
                .map(Dispute::getSubmitterId)
                .distinct()
                .toList();

        // submitterId -> nickname 벌크 조회 (N+1 방지)
        Map<Long, String> nicknameMap = userService.getUserNicknameMap(submitterIds);

        // DTO 변환
        Page<GetAdminDisputesResponseDto> response = disputes.map(dispute -> GetAdminDisputesResponseDto.of(
                dispute.getId(),
                dispute.getMatchId(),
                // getOrDefault -> 있으면 dispute.getSubmitterId(), 없으면 null 반환
                nicknameMap.getOrDefault(dispute.getSubmitterId(), null),
                dispute.getReason(),
                dispute.getStatus(),
                dispute.getSubmittedAt()
        ));

        return PageResponseDto.from(response);
    }


}
