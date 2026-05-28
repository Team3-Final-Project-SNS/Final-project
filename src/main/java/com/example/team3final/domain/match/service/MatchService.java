package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.*;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface MatchService {

    /**
     * 매칭 신청 / 생성 (선착순)
     *
     * @param postId      신청 대상 게시글 ID
     * @param applicantId 신청자 ID (Controller에서 인증 정보로 추출)
     * @return 생성된 매칭 정보
     */
    CreateMatchResponseDto createMatch(Long postId, Long applicantId);

    /**
     * 매칭 완료 처리 — 도메인 간 호출용 (만남 인증 QR 성공 시)
     * 오케스트레이션: Match→COMPLETED, Post→COMPLETED, 채팅방 비활성화, 양측 환불
     *
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    void completeMatch(Long matchId);

    // 등록자 노쇼
    void markAuthorNoShow(Long matchId);

    // 신청자 노쇼
    void markApplicantNoShow(Long matchId);

    // 양측 노쇼
    void markBothNoShow(Long matchId);

    /**
     * 매칭 취소 — Controller 직접 호출 (명세서 5.3)
     *
     * @param matchId 취소할 매칭 ID
     * @param userId  취소 요청자 ID (당사자 검증 + 50%/100% 구분)
     * @param request 취소 요청 DTO
     * @throws MatchException MATCH_001/002/006/007
     */
    CancelMatchResponseDto cancelMatch(Long matchId, Long userId, CancelMatchRequestDto request);


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
     * 매칭 상세 조회 — Controller 직접 호출 (명세서 5.2)
     *
     * @throws MatchException MATCH_001 — 매칭 없음 / MATCH_002 — 당사자 아님
     */
    GetMatchResponseDto getMatch(Long matchId, Long currentUserId);

    /**
     * 내 매칭 목록 조회 — Controller 직접 호출 (명세서 5.4)
     *
     * @param status null이면 전체 조회
     * @param pageable 페이징 + 정렬 (Controller에서 createdAt DESC로 생성)
     */
    PageResponseDto<GetMatchesResponseDto> getMatches(Long userId, MatchStatus status, Pageable pageable);

    /**
     * 매칭 정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: Meet 도메인 노쇼 일괄 판정(judgeGpsNoShow) — N건의 verification에 대해
     *         Match 정보를 한 번의 IN 쿼리로 가져와 N+1 문제 방지
     *
     * 반환 형태:
     *  - Key   = matchId
     *  - Value = MatchInfoDto
     *  - 호출 측에서 O(1) 룩업이 가능하도록 Map으로 반환
     *
     * Contract:
     *  - matchIds 가 비어있거나 null이면 빈 Map 반환 (예외 던지지 않음)
     *  - 존재하지 않는 matchId 가 섞여 있어도 예외를 던지지 않고, 결과 Map에서 빠진 채로 반환
     *    (단건 getMatchInfo와의 의도적인 차이 — 부분 실패가 전체 실패로 번지지 않도록)
     */
    Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds);
}
