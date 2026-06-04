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
import com.example.team3final.domain.meet.enums.VerificationStatus;
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
        applyDisputeJudgment(dispute, requestDto.getStatus(), match, postMatchInfo, submitterIsAuthor);

        if (requestDto.getStatus() == DisputeStatus.HOLD) {
            notificationPublisher.sendDisputePending(dispute.getSubmitterId(), disputeId);
        } else {
            // 나머지 판정은 일반 판정 결과 알림 발송 (HOLD 포함)
            notificationPublisher.sendDisputeResult(dispute.getSubmitterId(), disputeId);
        }

        // DTO 반환
        return AdminJudgeDisputeResponseDto.of(dispute, refundedPoint);
    }

    // 각 판정에 맞게 인증 상태, 매칭 상태, 게시글 상태, 채팅방 상태 정리 메서드
    private void applyDisputeJudgment(
            Dispute dispute,
            DisputeStatus judgment,
            Match match,
            PostMatchInfoDto postMatchInfo,
            boolean submitterIsAuthor) {

        // 보류 상태면 스킵
        if (judgment == DisputeStatus.HOLD) {
            return;
        }

        // 이의제기 대상 매칭의 인증 정보를 조회
        MeetVerification meetVerification = meetVerificationService.getByMatchId(dispute.getMatchId());

        // 이의제기 상태가 50% 부분 수용 판정인 경우
        if (judgment == DisputeStatus.PARTIALLY_ACCEPTED) {

            // DISPUTE 상태로 바꾸기 전에 저장해 둔 기존 노쇼 예정 상태를 복원
            VerificationStatus restoredStatus = meetVerification.restoreNoShowStatusFromDispute();

            // 이미 judgeDispute()에서 제출자에게 50% 반환했으므로,
            // 여기서는 포인트 정산 없이 Match/Post 상태만 노쇼 결과로 확정
            matchService.markNoShowByDisputeWithoutPointSettlement(dispute.getMatchId(), restoredStatus);

            // 관리자 판정이 끝나면 최종 노쇼 확정 상태로 변경
            meetVerification.confirmNoShow();

            // 노쇼로 종결된 매칭이므로 채팅방을 읽기 전용으로 전환
            chatService.makeReadOnlyChatRoom(match.getPostId());

            return;
        }

        // REJECTED인 경우
        if (judgment == DisputeStatus.REJECTED) {

            // DISPUTE 상태로 바꾸기 전의 기존 노쇼 예정 상태를 복원
            VerificationStatus restoredStatus = meetVerification.restoreNoShowStatusFromDispute();

            // 등록자가 노쇼였던 경우 일반 등록자 노쇼 정산을 확정
            if (restoredStatus == VerificationStatus.HOST_NO_SHOW) {
                matchService.markAuthorNoShow(dispute.getMatchId());

                // 신청자가 노쇼였던 경우 일반 신청자 노쇼 정산을 확정
            } else if (restoredStatus == VerificationStatus.GUEST_NO_SHOW) {
                matchService.markApplicantNoShow(dispute.getMatchId());

                // 양쪽 모두 노쇼였던 경우 양쪽 노쇼 정산을 확정
            } else if (restoredStatus == VerificationStatus.BOTH_NO_SHOW) {
                matchService.markBothNoShow(dispute.getMatchId());
            }

            // 관리자 판정이 끝났으므로 인증 상태를 최종 노쇼 확정 상태로 변경
            meetVerification.confirmNoShow();

            // 노쇼로 종결된 매칭이므로 채팅방을 읽기 전용으로 전환
            chatService.makeReadOnlyChatRoom(match.getPostId());

            return;
        }

        // 여기부터는 ACCEPTED 판정 흐름
        // 제출자 보증금 100% 반환은 judgeDispute()의 ACCEPTED 분기에서 이미 처리

        // 제출자가 등록자라면 상대방은 신청자이고, 제출자가 신청자라면 상대방은 등록자
        Long opponentId = submitterIsAuthor ? match.getApplicantId() : postMatchInfo.authorId();

        // 상대방에게 반환할 보증금 금액을 계산
        int opponentDeposit = submitterIsAuthor ? match.getApplicantDeposit() : postMatchInfo.authorDeposit();

        // ACCEPTED에서는 제출자뿐 아니라 상대방 보증금도 묶어둘 이유가 없으므로 전액 반환
        userPointService.refundPoint(opponentId, opponentDeposit, dispute.getMatchId());

        // 이의제기 사유가 만남 완료 인정 유형인지, 매칭 취소 인정 유형인지 확인
        DisputeType disputeType = dispute.getDisputeType();

        // 실제 만남이 있었지만 QR/GPS/스마트폰 문제로 인증만 실패한 경우
        if (disputeType.isMeetCompletionType()) {

            // 인증 상태를 DONE으로 변경
            meetVerification.completeByDispute();

            // Match/Post를 완료 상태로 변경
            matchService.completeMatchByDispute(dispute.getMatchId());

            // 정상 완료 흐름처럼 채팅방 비활성화를 예약
            chatService.scheduleChatRoomDeactivation(match.getPostId());


            return;
        }

        // 관리자가 노쇼가 아니라 매칭 취소로 보는 것이 맞다고 판단한 경우
        if (disputeType.isMatchCancelType()) {

            // 인증 상태를 NO_SHOW_CANCELLED로 변경
            meetVerification.cancelNoShowByDispute();

            // Match/Post를 취소 상태로 변경
            matchService.cancelMatchByDispute(dispute.getMatchId());

            // 취소된 매칭이므로 채팅방을 읽기 전용으로 전환
            chatService.makeReadOnlyChatRoom(match.getPostId());
        }
    }

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
