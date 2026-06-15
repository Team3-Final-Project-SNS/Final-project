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

// Match 도메인의 내부 조회 기능을 다른 도메인에 제공하는 서비스
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

        // [1] 빈 리스트 가드
        //     - null 체크: 호출 측의 실수 방지 (NPE 던지지 않고 빈 결과로 처리)
        //     - isEmpty 체크: IN 절에 빈 컬렉션을 넣으면 일부 DB(특히 Oracle)에서 SQL 문법 오류 발생
        //     - Collections.emptyMap()을 쓰는 이유: new HashMap<>()보다 불변/싱글톤이라 GC 부담 ↓
        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // [2] findAllById는 JpaRepository 기본 제공 메서드
        //     내부적으로 SELECT * FROM matches WHERE match_id IN (?, ?, ?, ...) 쿼리 1번으로 변환됨
        //     ※ 존재하지 않는 ID가 섞여 있으면 결과에서 그냥 빠짐 (예외 안 던짐)
        //        → 위 JavaDoc의 Contract와 자연스럽게 일치
        List<Match> matches = matchRepository.findAllById(matchIds);

        // [3] List<Match> → Map<Long, MatchInfoDto> 변환
        //     - keyMapper:   Match::getId         → Key로 사용할 값 추출 (matchId)
        //     - valueMapper: MatchInfoDto::from   → Value로 변환할 함수 (기존 단건 메서드와 동일한 변환기 재사용)
        //     ※ matchId는 PK라 중복될 수 없으므로 mergeFunction 인자는 불필요
        return matches.stream()
                .collect(Collectors.toMap(
                        Match::getId,
                        MatchInfoDto::from
                ));
    }

    @Override
    public boolean hasAppliedToPost(Long postId, Long applicantId) {
        // 중복 신청 여부는 Match 도메인의 데이터 규칙이므로,
        // 다른 도메인은 Repository 대신 이 서비스 메서드를 통해 확인합니다.
        return matchRepository.existsByPostIdAndApplicantIdAndStatus(postId, applicantId, MatchStatus.MATCHED);
    }

    @Override
    public List<Long> getMatchIdsByPostId(Long postId) {
        // Review 도메인에서 단체 만남 리뷰 평균을 계산할 때 사용합니다.
        // Service-to-Service 규칙에 따라 Review는 MatchRepository를 직접 참조하지 않습니다.
        return matchRepository.findAllByPostId(postId)
                .stream()
                .map(Match::getId)
                .toList();
    }

    @Override
    public List<Match> getCompletedMatchesByPostId(Long postId) {
        // Chat 도메인에서 만남 완료 알림 대상 신청자를 조회할 때 사용합니다.
        return matchRepository.findAllByPostIdAndStatus(postId, MatchStatus.COMPLETED);
    }

    @Override
    public Optional<Match> findCompletedMatchById(Long matchId) {
        // Review 도메인 스케줄러에서 후기 마지막 날 알림 대상 매칭을 조회할 때 사용합니다.
        return matchRepository.findById(matchId)
                .filter(match -> match.getStatus() == MatchStatus.COMPLETED);
    }

    // 사용자가 등록자 또는 신청자로 참여한 전체 매칭 ID 목록 조회
    // MeetVerification 도메인에서 노쇼 예정 매칭 필터링 시 사용
    // matchId 목록만 반환해 불필요한 Match 엔티티 로딩을 줄임
    @Override
    public List<Long> getAllMatchIdsByUserId(Long userId) {
        return matchRepository.findAllMatchIdsByUserId(userId);
    }

    // postId 기준으로 현재 활성(MATCHED) 상태의 매칭 ID를 조회한다.
    // 사용처: MeetReminderScheduler에서 postId만 갖고 있을 때 알림 relatedId로 쓸 matchId를 구하기 위함
    @Override
    public Optional<Long> getActiveMatchIdByPostId(Long postId) {
        return matchRepository
                .findFirstByPostIdAndStatusOrderByIdAsc(postId, MatchStatus.MATCHED)
                .map(Match::getId);
    }
}
