package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Match 내부 조회 기능 제공
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchInternalServiceImpl implements MatchInternalService {

    private final MatchRepository matchRepository;

    @Override
    public Match getMatchById(Long matchId) {

        return matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchException(ErrorCode.MATCH_NOT_FOUND));
    }

    @Override
    public MatchInfoDto getMatchInfo(Long matchId) {

        Match match = getMatchById(matchId);

        return MatchInfoDto.from(match);
    }

    @Override
    public Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds) {

        // 빈 ID 목록은 DB 조회 없이 빈 결과 반환
        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // matchId 목록 기준 MatchInfoDto 일괄 변환
        List<Match> matches = matchRepository.findAllById(matchIds);

        return matches.stream()
                .collect(Collectors.toMap(
                        Match::getId,
                        MatchInfoDto::from
                ));
    }

    @Override
    public boolean hasAppliedToPost(Long postId, Long applicantId) {
        // MATCHED 상태 기준 중복 신청 여부 확인
        return matchRepository.existsByPostIdAndApplicantIdAndStatus(postId, applicantId, MatchStatus.MATCHED);
    }

    @Override
    public List<Long> getMatchIdsByPostId(Long postId) {
        // 후기 도메인 계산용 postId 기준 matchId 목록
        return matchRepository.findAllByPostId(postId)
                .stream()
                .map(Match::getId)
                .toList();
    }

    @Override
    public List<Match> getCompletedMatchesByPostId(Long postId) {
        // 완료된 만남 알림 대상 신청자 조회
        return matchRepository.findAllByPostIdAndStatus(postId, MatchStatus.COMPLETED);
    }

    @Override
    public Optional<Match> findCompletedMatchById(Long matchId) {
        // 후기 작성 가능 여부 확인용 완료 매칭 조회
        return matchRepository.findById(matchId)
                .filter(match -> match.getStatus() == MatchStatus.COMPLETED);
    }

    @Override
    public List<Long> getAllMatchIdsByUserId(Long userId) {
        // 사용자가 작성자 또는 신청자인 전체 matchId 조회
        return matchRepository.findAllMatchIdsByUserId(userId);
    }

    @Override
    public List<Long> getActiveMatchIdsByPostId(Long postId) {
        // 그룹 만남 알림용 활성 matchId 전체 조회
        return matchRepository.findAllByPostIdAndStatusOrderByIdAsc(postId, MatchStatus.MATCHED)
                .stream()
                .map(Match::getId)
                .toList();
    }
}
