package com.example.team3final.domain.review.service;

import com.example.team3final.domain.review.entity.UserAvoidRelation;
import com.example.team3final.domain.review.repository.UserAvoidRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 다시 만나고 싶지 않아요 관계를 관리하는 서비스 구현체입니다.
 * 이 서비스는 Match/Post 도메인에서도 사용되므로,
 * ReviewServiceImpl과 분리해 순환 참조를 피합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewAvoidanceServiceImpl implements ReviewAvoidanceService {

    private final UserAvoidRelationRepository userAvoidRelationRepository;

    @Override
    public List<Long> getAvoidedUserIds(Long userId) {
        return userAvoidRelationRepository.findAllByUserId(userId)
                .stream()
                .map(UserAvoidRelation::getAvoidedUserId)
                .toList();
    }

    @Override
    public boolean existsAvoidRelation(Long userId, Long otherUserId) {
        return userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(userId, otherUserId);
    }

    @Override
    @Transactional
    public void createAvoidRelation(Long userId, Long avoidedUserId, Long reviewId) {
        // 신청자 -> 등록자 방향을 저장합니다.
        createAvoidRelationIfNotExists(userId, avoidedUserId, reviewId);

        // 블라인드 정책은 양쪽 모두 서로의 게시글을 보지 않는 것이므로 반대 방향도 저장합니다.
        createAvoidRelationIfNotExists(avoidedUserId, userId, reviewId);
    }

    private void createAvoidRelationIfNotExists(Long userId, Long avoidedUserId, Long reviewId) {
        if (userAvoidRelationRepository.existsByUserIdAndAvoidedUserId(userId, avoidedUserId)) {
            return;
        }

        userAvoidRelationRepository.save(
                UserAvoidRelation.builder()
                        .userId(userId)
                        .avoidedUserId(avoidedUserId)
                        .reviewId(reviewId)
                        .build()
        );
    }
}
