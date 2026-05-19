package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.entity.Match;

public interface MatchQueryService {

    /**
     * 매칭 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     *
     * 사용처:
     * - 채팅(박): 채팅방 입장 권한 검증 시 등록자/신청자 ID 확인
     * - GPS 인증(정): 약속 장소/시간 정보 확인
     * - 매칭 도메인 내부: 매칭 취소/완료 시 매칭 로딩
     *
     * @param matchId 조회할 매칭 ID
     * @return Match 엔티티
     * @throws MatchException MATCH_001 — 매칭이 존재하지 않음
     */
    Match getMatchById(Long matchId);
}
