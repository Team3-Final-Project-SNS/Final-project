package com.example.team3final.domain.match.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.GetMatchResponseDto;
import com.example.team3final.domain.match.dto.response.GetMatchesResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import org.springframework.data.domain.Pageable;

// Match 도메인의 조회 전용 기능을 담당하는 서비스
public interface MatchQueryService {

    /**
     * 매칭 상세 조회 — Controller 직접 호출 (명세서 5.2)
     *
     * @throws MatchException MATCH_001 — 매칭 없음 / MATCH_002 — 당사자 아님
     */
    GetMatchResponseDto getMatch(Long matchId, Long currentUserId);

    /**
     * 내 매칭 목록 조회 — Controller 직접 호출 (명세서 5.4)
     * @param status null이면 전체 조회
     * @param pageable 페이징 + 정렬 (Controller에서 createdAt DESC로 생성)
     */
    PageResponseDto<GetMatchesResponseDto> getMatches(
            Long userId,
            MatchStatus status,
            Pageable pageable
    );
}
