package com.example.team3final.domain.match.service;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MatchInternalService {

    // 단건 매칭 엔티티 조회
    Match getMatchById(Long matchId);

    // 단건 매칭 정보 DTO 조회
    MatchInfoDto getMatchInfo(Long matchId);

    // 여러 matchId의 매칭 정보 일괄 조회
    Map<Long, MatchInfoDto> getMatchInfos(List<Long> matchIds);

    // 특정 신청자의 특정 게시글 신청 여부
    boolean hasAppliedToPost(Long postId, Long applicantId);

    // 게시글 기준 전체 matchId 목록
    List<Long> getMatchIdsByPostId(Long postId);

    // 게시글 기준 완료된 매칭 목록
    List<Match> getCompletedMatchesByPostId(Long postId);

    // 완료 상태 매칭 단건 조회
    Optional<Match> findCompletedMatchById(Long matchId);

    // 사용자가 당사자인 전체 matchId 목록
    List<Long> getAllMatchIdsByUserId(Long userId);

    // 그룹 만남 알림 판단용 postId 기준 활성 matchId 목록
    List<Long> getActiveMatchIdsByPostId(Long postId);
}
