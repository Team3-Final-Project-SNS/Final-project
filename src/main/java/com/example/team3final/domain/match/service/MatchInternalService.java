package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Match 도메인의 내부 조회 기능을 다른 도메인에 제공하는 서비스
public interface MatchInternalService {

    /**
     * 매칭 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     *
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    Match getMatchById(Long matchId);

    /**
     * 매칭 정보 조회 — 도메인 간 호출용 (DTO 반환)
     *
     * @throws MatchException MATCH_001
     */
    MatchInfoDto getMatchInfo(Long matchId);

    /**
     * 매칭 정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: Meet 도메인 노쇼 일괄 판정(judgeGpsNoShow) — N건의 verification에 대해
     *         Match 정보를 한 번의 IN 쿼리로 가져와 N+1 문제 방지
     * 반환 형태:
     *  - Key   = matchId
     *  - Value = MatchInfoDto
     *  - 호출 측에서 O(1) 룩업이 가능하도록 Map으로 반환
     * Contract:
     *  - matchIds 가 비어있거나 null이면 빈 Map 반환 (예외 던지지 않음)
     *  - 존재하지 않는 matchId 가 섞여 있어도 예외를 던지지 않고, 결과 Map에서 빠진 채로 반환
     *    (단건 getMatchInfo와의 의도적인 차이 — 부분 실패가 전체 실패로 번지지 않도록)
     */
    Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds);

    /**
     * 특정 사용자가 특정 게시글에 이미 신청했는지 확인합니다.
     * AI 매칭, 게시글 검증 등 다른 도메인에서 중복 신청 여부만 필요할 때
     * MatchRepository를 직접 참조하지 않고 Match 도메인 서비스로 조회합니다.
     */
    boolean hasAppliedToPost(Long postId, Long applicantId);

    /**
     * 특정 게시글에 생성된 매칭 ID 목록을 조회합니다.
     * Review 도메인이 단체 만남의 리뷰 평균을 계산할 때
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    List<Long> getMatchIdsByPostId(Long postId);

    /**
     * 특정 게시글에 속한 COMPLETED 매칭 목록을 조회합니다.
     * Chat 도메인이 만남 완료 후 채팅방 READ_ONLY 전환 알림을 보낼 때
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    List<Match> getCompletedMatchesByPostId(Long postId);

    /**
     * COMPLETED 상태의 매칭을 Optional로 조회합니다.
     * Review 도메인이 후기 작성 마지막 날 알림 처리 시
     * MatchRepository를 직접 참조하지 않도록 서비스 메서드로 제공합니다.
     */
    Optional<Match> findCompletedMatchById(Long matchId);

    // 사용자가 당사자인 전체 매칭 ID 목록 조회
    List<Long> getAllMatchIdsByUserId(Long userId);

    // postId 기준 현재 활성(MATCHED) 매칭의 ID 조회
    // 활성 매칭이 없으면 Optional.empty() 반환 (이미 취소된 경우)
    Optional<Long> getActiveMatchIdByPostId(Long postId);
}
