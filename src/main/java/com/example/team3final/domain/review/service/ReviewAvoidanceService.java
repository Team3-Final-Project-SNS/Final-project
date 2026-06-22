package com.example.team3final.domain.review.service;

import java.util.List;

/**
 * 다시 만나고 싶지 않아요 관계를 다른 도메인에 제공하는 서비스입니다.
 * Post, Match 도메인은 ReviewRepository를 직접 참조하지 않고,
 * 이 서비스를 통해 블라인드 처리에 필요한 사용자 관계만 조회합니다.
 */
public interface ReviewAvoidanceService {

    /**
     * 현재 사용자가 다시 만나고 싶지 않은 사용자 ID 목록을 조회합니다.
     */
    List<Long> getAvoidedUserIds(Long userId);

    /**
     * 두 사용자 사이에 블라인드 관계가 있는지 확인합니다.
     */
    boolean existsAvoidRelation(Long userId, Long otherUserId);

    /**
     * 다시 만나고 싶지 않아요 관계를 양방향으로 저장합니다.
     */
    void createAvoidRelation(Long userId, Long avoidedUserId, Long reviewId);
}
