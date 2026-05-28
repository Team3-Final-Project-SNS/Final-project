package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final MatchService matchService;
    private final PostService postService;
    private final MeetVerificationService meetVerificationService;

    /**
     * 이의제기 제출
     */
    @Override
    @Transactional
    public CreateDisputeResponseDto createDispute(Long matchId, Long userId, CreateDisputeRequestDto request) {

        // 1. 매칭 존재 확인
        MatchInfoDto match = matchService.getMatchInfo(matchId);

        // 2. 등록자 조회
        PostInfoDto post = postService.getPostInfo(match.postId());
        Long authorId = post.authorId();

        // 3. 당사자 검증
        if (!match.isParticipant(userId, authorId)) {
            throw new DisputeException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 4. 만남 인증 정보 조회
        MeetVerificationResponseDto meet = meetVerificationService.getMeetVerification(userId, matchId);

        // 5. 노쇼 예정 상태 검증
        VerificationStatus status = meet.verificationStatus();
        boolean isNoShow = status == VerificationStatus.HOST_NO_SHOW
                || status == VerificationStatus.GUEST_NO_SHOW
                || status == VerificationStatus.BOTH_NO_SHOW;

        if (!isNoShow) {
            throw new DisputeException(ErrorCode.DISPUTE_NOT_NO_SHOW);
        }

        // 6. 장소 인증(GPS) 완료 검증
        // 요청자가 등록하면 등록자 GPS 진입 시각을, 신청자면 신청자 GPS 진입 시각을 본다.
        boolean isRequesterHost = userId.equals(authorId);
        boolean placeVerified = isRequesterHost
                ? meet.authorPlaceVerifiedAt() != null
                : meet.applicantPlaceVerifiedAt() != null;
        if (!placeVerified) {
            throw new DisputeException(ErrorCode.DISPUTE_PLACE_NOT_VERIFIED);
        }

        // 7. 중복 제출 검증
        if (disputeRepository.existsByMatchIdAndSubmitterId(matchId, userId)) {
            throw new DisputeException(ErrorCode.DISPUTE_ALREADY_SUBMITTED);
        }

        // 8. 48시간 제한 검증
        LocalDateTime decidedAt = meet.noShowDecidedAt();
        if (decidedAt == null || Duration.between(decidedAt, LocalDateTime.now()).toHours() >= 24L) {
                throw new DisputeException(ErrorCode.DISPUTE_DEADLINE_EXCEEDED);
        }

        // 9. 저장
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(userId)
                .reason(request.getReason())
                .build();
        Dispute saved = disputeRepository.save(dispute);

        return CreateDisputeResponseDto.from(saved);
    }

    /**
     * 내 이의제기 상태 조회
     */
    @Override
    @Transactional(readOnly = true)
    public DisputeResponseDto getDispute(Long matchId, Long userId) {

        // 1. 매칭 존재 확인
        matchService.getMatchInfo(matchId);

        Dispute dispute = disputeRepository.findByMatchIdAndSubmitterId(matchId, userId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_NOT_FOUND));

        return DisputeResponseDto.from(dispute);
    }

    // 어드민 이의제기 상세 조회용 - disputeId 단건 조회
    @Override
    public Dispute getDisputeById(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_NOT_FOUND));
    }

    @Override
    public Page<Dispute> getDisputesForAdmin(DisputeStatus status, Pageable pageable) {
        // status가 null이면 전체 조회, 있으면 해당 status만 필터링
        if (status == null) {
            return disputeRepository.findAll(pageable);
        }
        return disputeRepository.findAllByStatus(status, pageable);
    }
}
