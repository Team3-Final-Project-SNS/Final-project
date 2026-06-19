package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.context.MeetVerificationContext;
import com.example.team3final.domain.meet.dto.response.GetMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetQrSupport;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// MeetVerification 도메인의 조회 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetVerificationQueryServiceImpl implements MeetVerificationQueryService {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final UserInternalService userInternalService;
    private final MeetVerificationContextReader contextReader;
    private final MeetQrSupport meetQrSupport;

    // QR 토큰 조회
    // 등록자가 QR 화면을 열면 호출됨 — 장소 인증 완료 후 QR 토큰을 발급하거나 기존 토큰을 반환
    @Override
    @Transactional // QR 토큰 발급 시 DB 쓰기가 발생하므로 readOnly 제외
    public QrResponseDto getMeetQrByPost(Long userId, Long postId) {

        // Post 정보를 조회해서 요청자가 등록자인지 확인
        PostInfoDto postInfoDto = postInternalService.getPostInfo(postId);

        // QR은 등록자만 화면에 띄울 수 있으므로, 작성자 검증
        if (!userId.equals(postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.QR_NOT_AUTHOR);
        }

        // 같은 Post에 속한 활성 Match ID만 조회
        List<Long> siblingMatchIds = matchInternalService.getActiveMatchIdsByPostId(postId);

        // 매칭이 하나도 없다면 아직 QR을 발급할 수 없는 상태
        if (siblingMatchIds.isEmpty()) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // 같은 Post에 속한 MeetVerification을 벌크 조회 (N+1 방지)
        List<MeetVerification> siblingMvList = meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        // QR 발급 시간 판정은 연장 시간이 있으면 연장된 만남 시각을 기준으로 한다.
        // 연장된 만남이 없다면 최초 게시글 만남 시각을 그대로 사용한다.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveMeetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByPostId(postId)
                .orElse(postInfoDto.meetAt());

        // QR은 전체 GPS 완료 또는 기준 시각 10분 경과 시 열림
        boolean allPlaceVerified = siblingMvList.stream()
                .allMatch(mv -> mv.isAuthorPlaceVerified() && mv.isApplicantPlaceVerified());
        boolean isQrFallbackTime = !now.isBefore(
                effectiveMeetAt.plusMinutes(MeetVerificationPolicy.NO_SHOW_JUDGE_MINUTES)
        );

        // QR 화면은 등록자도 GPS 인증을 먼저 완료한 경우에만 진입 가능
        boolean authorVerified = siblingMvList.stream().anyMatch(MeetVerification::isAuthorPlaceVerified);
        if (!authorVerified || (!allPlaceVerified && !isQrFallbackTime)) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // 기존 공통 QR 토큰이 있으면 재사용하고, 누락된 활성 MeetVerification에는 같은 토큰과 만료 시각을 채운다.
        MeetVerification tokenOwner = meetQrSupport.issuePostQrTokenIfNeeded(postId, now)
                .orElseThrow(() -> new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED));

        // 공통 QR 토큰의 만료 여부를 확인
        if (tokenOwner.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        // Post 기준 공통 QR 응답을 반환
        return QrResponseDto.of(
                postId,
                tokenOwner.getQrToken(),
                tokenOwner.getQrExpiresAt()
        );
    }

    // 만남 인증 상태 조회
    // 매칭 당사자가 현재 인증 진행 상황을 확인할 때 호출됨
    @Override
    public MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetVerificationContext ctx = contextReader.loadMeetContext(matchId);

        MatchInfoDto matchInfo = ctx.matchInfo();
        PostInfoDto postInfo = ctx.postInfo();

        // 매칭 당사자 검증 (등록자인지 신청자인지)
        contextReader.validateParticipant(userId, matchInfo, postInfo);

        // 등록자 닉네임 조회
        String authorNickname = userInternalService.getUserInfo(postInfo.authorId()).nickname();

        // QR 인증 현황은 진행 중이거나 방금 완료된 Match까지 포함해서 조회
        Map<Long, MatchInfoDto> siblingMatchInfoMap = matchInternalService.getMatchInfos(
                matchInternalService.getMatchIdsByPostId(matchInfo.postId())
        ).entrySet().stream()
                .filter(entry -> entry.getValue().status() == MatchStatus.MATCHED
                        || entry.getValue().status() == MatchStatus.COMPLETED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<Long> siblingMatchIds = siblingMatchInfoMap.keySet().stream().toList();

        // 형제 matchId → MeetVerification 목록 벌크 조회 (N+1 방지)
        List<MeetVerification> siblingMvList = meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        // 신청자 userId 목록 추출
        List<Long> applicantIds = siblingMatchInfoMap.values().stream()
                .map(MatchInfoDto::applicantId)
                .toList();

        // 신청자 닉네임 벌크 조회
        Map<Long, String> nicknameMap = userInternalService.getUserNicknameMap(applicantIds);

        // matchId → ParticipantInfo 맵 조립
        Map<Long, MeetVerificationResponseDto.ParticipantInfo> applicantInfoMap =
                siblingMatchInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new MeetVerificationResponseDto.ParticipantInfo(
                                        e.getValue().applicantId(),
                                        nicknameMap.getOrDefault(e.getValue().applicantId(), "알 수 없음")
                                )
                        ));

        return MeetVerificationResponseDto.of(
                matchId,
                ctx.meetVerification(),
                authorNickname,
                siblingMvList,
                applicantInfoMap
        );
    }

    // 만남 시간 연장 상태 조회
    // 연장 요청 화면 진입 시 현재 연장 요청 상태와 요청자 정보를 반환
    @Override
    public GetMeetExtensionResponseDto getMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetVerificationContext ctx = contextReader.loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 매칭 당사자 확인
        contextReader.validateParticipant(userId, matchInfoDto, postInfoDto);

        // NONE 상태면 아직 요청자 없음 → 닉네임 null 처리
        String requesterNickname = null;
        if (meetVerification.getExtensionRequesterId() != null) {
            requesterNickname = userInternalService.getUserInfo(meetVerification.getExtensionRequesterId()).nickname();
        }

        return GetMeetExtensionResponseDto.of(meetVerification, requesterNickname, postInfoDto.meetAt(), userId);
    }
}
