package com.example.team3final.domain.meet.service.support;

import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Qr 보조 컴포넌트
@Component
@RequiredArgsConstructor
public class MeetQrSupport {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;

    // QR 토큰 발급 — 중복 발급 방지 포함
    // 호출 시점: 양측 GPS 인증 완료 직후
    public void issueQrTokenIfNeeded(MeetVerification meetVerification, Long postId) {

        // 현재 MeetVerification에 이미 QR 토큰이 있으면 중복 발급 X
        if (meetVerification.getQrToken() != null) {
            return;
        }

        // 등록자와 신청자 양측 장소 인증이 끝나지 않았으면 QR을 발급 X
        if (meetVerification.getAuthorPlaceVerifiedAt() == null
                || meetVerification.getApplicantPlaceVerifiedAt() == null) {
            return;
        }

        // 같은 Post에 이미 발급된 공통 QR 토큰이 있으면 재사용
        // 없으면 새 공통 QR 토큰을 생성
        String sharedToken = getPostQrTokenOwner(postId)
                .map(MeetVerification::getQrToken)
                .orElseGet(() -> "hp_qr_" + UUID.randomUUID().toString().replace("-", ""));

        // 등록자와 신청자의 장소 인증 시각 중 더 늦은 시각을 기준으로 QR 만료 시간을 계산
        LocalDateTime placeVerifiedCompletedAt = meetVerification.getAuthorPlaceVerifiedAt()
                .isAfter(meetVerification.getApplicantPlaceVerifiedAt())
                ? meetVerification.getAuthorPlaceVerifiedAt()
                : meetVerification.getApplicantPlaceVerifiedAt();

        // QR 만료 시각은 양측 장소 인증 완료 시각 기준 30분 이후
        LocalDateTime expiresAt =
                placeVerifiedCompletedAt.plusMinutes(MeetVerificationPolicy.QR_TOKEN_VALIDITY_MINUTES);

        // 현재 MeetVerification에 Post 공통 QR 토큰과 만료 시각을 저장
        meetVerification.issueQrToken(sharedToken, expiresAt);
    }

    // postId 기준으로 이미 발급된 공통 QR 토큰 Owner를 조회
    // QR 토큰이 저장된 MeetVerification 하나를 공통 토큰 owner로 사용
    public Optional<MeetVerification> getPostQrTokenOwner(Long postId) {

        // 같은 Post에 속한 모든 Match ID를 조회
        List<Long> siblingMatchIds = matchInternalService.getMatchIdsByPostId(postId);

        // 같은 Post에 Match가 없다면 공통 QR 토큰도 존재할 수 없음
        if (siblingMatchIds.isEmpty()) {
            return Optional.empty();
        }

        // 같은 Post에 속한 MeetVerification을 한 번에 조회
        List<MeetVerification> siblingMvList =
                meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        // 조회된 목록에서 QR 토큰을 가진 MeetVerification을 찾기
        return findPostQrTokenOwner(siblingMvList);
    }

    // MeetVerification 목록에서 QR 토큰을 가진 항목 하나를 찾기,
    // 이미 발급된 Post 공통 QR 토큰이 있는지 확인하는 공통메서드
    // 없으면 null을 반환해 호출부에서 최초 발급
    public Optional<MeetVerification> findPostQrTokenOwner(List<MeetVerification> meetVerifications) {

        // QR 토큰이 null이 아닌 MeetVerification을 하나 찾는다.
        return meetVerifications.stream()
                .filter(mv -> mv.getQrToken() != null)
                .findFirst();
    }
}

