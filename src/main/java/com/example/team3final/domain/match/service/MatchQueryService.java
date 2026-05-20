package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;

public interface MatchQueryService {

    /**
     * 매칭 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     *
     * 사용처:
     * - 채팅: 채팅방 입장 권한 검증 시 등록자/신청자 ID 확인
     * - GPS 인증: 약속 장소/시간 정보 확인
     * - 매칭 도메인 내부: 매칭 취소/완료 시 매칭 로딩
     *
     * @param matchId 조회할 매칭 ID
     * @return Match 엔티티
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    Match getMatchById(Long matchId);

    /**
     * 매칭 정보 조회 — 도메인 간 호출용 (DTO 반환)
     *
     * 사용처:
     * - 채팅: 채팅방 입장 권한 검증
     * - GPS 인증: QR 스캔/만남 인증 시 당사자 검증
     *
     * ※ getMatchById와의 차이: 엔티티가 아닌 DTO 반환 → 권한 검증 메서드(isParticipant, isApplicant) 포함
     *
     * @throws MatchException MATCH_001
     */
    MatchInfoDto getMatchInfo(Long matchId);

    /**
     * 매칭 상세 조회 — Controller에서 직접 호출 (명세서 5.2 getMatch)
     *
     * @param matchId       조회할 매칭 ID
     * @param currentUserId 현재 로그인 유저 ID (당사자 검증용)
     * @return 매칭 상세 응답 DTO
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     * @throws MatchException MATCH_002 — 당사자가 아님
     */
    GetMatchResponseDto getMatch(Long matchId, Long currentUserId);
}
