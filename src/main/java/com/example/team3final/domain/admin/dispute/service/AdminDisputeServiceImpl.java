package com.example.team3final.domain.admin.dispute.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminOverrideDisputeStatusRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputesResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostMatchInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.service.UserPointService;
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
    private final PostService postService;
    private final UserPointService userPointService;
    private final NotificationPublisher notificationPublisher;

    // 이의제기 상세 조회 API
    @Override
    @Transactional // SUBMITTED -> UNDER_REVIEW 상태 변경이 일어나므로, 쓰기 적용
    public GetAdminDisputeResponseDto getDispute(Long adminId, Long disputeId) {

        // 어드민 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 이의 제기 조회
        Dispute dispute = disputeService.getDisputeById(disputeId);

        // SUBMITTED 상태면 UNDER_REVIEW로 자동 전환
        if (dispute.getStatus() == DisputeStatus.SUBMITTED) {
            dispute.startReview(adminId);
        }

        // 제출자 닉네임 조회
        String applicantNickname = userService.getUserInfo(dispute.getSubmitterId()).nickname();

        // matchId는 postId로 조회
        Long postId = matchService.getMatchInfo(dispute.getMatchId()).postId();

        // 만남인증 정보 조회 (GPS 인증 시각 포함)
        MeetVerification meetVerification = meetVerificationService.getByMatchId(dispute.getMatchId());

        // 채팅 내역 조회 — chatRoomId 없으면 빈 리스트
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
                dispute.getDisputeType(),
                dispute.getReason(),
                dispute.getStatus(),
                meetVerification.getStatus(),
                meetVerification.getAuthorPlaceVerifiedAt(),
                meetVerification.getApplicantPlaceVerifiedAt(),
                dispute.getCreatedAt(),
                chatMessages
        );
    }

    @Override
    public PageResponseDto<GetAdminDisputesResponseDto> getDisputes(Long adminId, DisputeStatus status, Pageable pageable) {

        // 어드민 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

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
                dispute.getCreatedAt()
        ));

        return PageResponseDto.from(response);
    }

    // 이의제기 최종 판정
    @Override
    @Transactional
    public AdminJudgeDisputeResponseDto judgeDispute(Long adminId, Long disputeId, AdminJudgeDisputeRequestDto requestDto) {

        // 어드민 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 이의제기 조회
        Dispute dispute = disputeService.getDisputeById(disputeId);

        // 이미 종결된 이의제기 인지 확인
        if (dispute.getStatus().isClosed()) {
            throw new AdminException(ErrorCode.ADMIN_DISPUTE_ALREADY_PROCESSED);
        }

        // UNDER_REVIEW 상태가 맞는지 확인
        if (dispute.getStatus() != DisputeStatus.UNDER_REVIEW) {
            throw new AdminException(ErrorCode.ADMIN_DISPUTE_NOT_UNDER_REVIEW);
        }

        // 매칭 정보 조회
        Match match = matchService.getMatchById(dispute.getMatchId());

        // Post 정보 조회
        PostMatchInfoDto postMatchInfo = postService.getPostMatchInfo(match.getPostId());

        // submitterId = authorId 이면 등록자, 아니면 신청자
        boolean submitterIsAuthor = dispute.getSubmitterId().equals(postMatchInfo.authorId());

        // 이의제기자의 예치금 결정
        int deposited = submitterIsAuthor ? postMatchInfo.authorDeposit() : match.getApplicantDeposit();

        // 판정 결과에 따른 포인트 처리
        int refundedPoint = switch (requestDto.getStatus()) {

            case ACCEPTED -> {
                // 전액 100% 반환
                userPointService.refundPoint(dispute.getSubmitterId(), deposited, dispute.getMatchId());
                dispute.process(DisputeStatus.ACCEPTED, adminId, requestDto.getComment());
                yield deposited;
            }

            case PARTIALLY_ACCEPTED -> {
                // 50%만 반환
                userPointService.partialRefundPoint(dispute.getSubmitterId(), deposited, dispute.getMatchId());
                dispute.process(DisputeStatus.PARTIALLY_ACCEPTED, adminId, requestDto.getComment());
                yield deposited / 2;
            }

            case REJECTED -> {
                // 반환값 없음
                dispute.process(DisputeStatus.REJECTED, adminId, requestDto.getComment());
                yield 0;
            }

            case HOLD -> {
                // 보류 : 포인트 처리 없음,
                // holdAt 기록 -> 재이의제기 24시간 카운팅 시작
                dispute.hold(adminId, requestDto.getComment());
                yield 0;
            }

            default -> throw new AdminException(ErrorCode.ADMIN_DISPUTE_INVALID_STATUS);
        };

        // HOLD는 전용 알림 발송 (24시간 이내 행동 유도 메시지)
        if (requestDto.getStatus() == DisputeStatus.HOLD) {
            notificationPublisher.sendDisputePending(dispute.getSubmitterId(), disputeId);
        } else {
            // 나머지 판정은 일반 판정 결과 알림 발송 (HOLD 포함)
            notificationPublisher.sendDisputeResult(dispute.getSubmitterId(), disputeId);
        }

        // DTO 반환
        return AdminJudgeDisputeResponseDto.of(dispute, refundedPoint);
    }

    // 이의제기 상태 강제 변경 (오판정 정정용)
    @Override
    @Transactional
    public AdminJudgeDisputeResponseDto overrideDisputeStatus(Long adminId, Long disputeId, AdminOverrideDisputeStatusRequestDto requestDto) {

        // 어드민 존재 확인
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 이의 제기 조회
        Dispute dispute = disputeService.getDisputeById(disputeId);

        // 상태 전이 제약 없이 강제 변경
        dispute.forceChangeStatus(requestDto.getStatus(), adminId, requestDto.getComment());

        // 강제 변경도 유저에게 알림 발송
        notificationPublisher.sendDisputeResult(dispute.getSubmitterId(), disputeId);

        return AdminJudgeDisputeResponseDto.of(dispute, 0);
    }
}
