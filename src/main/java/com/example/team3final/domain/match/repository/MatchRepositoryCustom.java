package com.example.team3final.domain.match.repository;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MatchRepositoryCustom {

    // 내 매칭 목록 전체 조회 (상태 필터 없음)
    Page<Match> findAllByUserId(Long userId, Pageable pageable);

    // 내 매칭 목록 상태 필터 조회
    Page<Match> findAllByUserIdAndStatus(Long userId, MatchStatus status, Pageable pageable);
}
