package com.example.team3final.domain.review.repository;

import com.example.team3final.domain.review.entity.UserAvoidRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAvoidRelationRepository extends JpaRepository<UserAvoidRelation, Long> {

    boolean existsByUserIdAndAvoidedUserId(Long userId, Long avoidedUserId);

    List<UserAvoidRelation> findAllByUserId(Long userId);
}
