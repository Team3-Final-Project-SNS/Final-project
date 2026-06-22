package com.example.team3final.domain.ai.report.repository;

import com.example.team3final.domain.ai.report.entity.AiAdminResult;
import com.example.team3final.domain.ai.report.enums.AiAdminCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAdminResultRepository extends JpaRepository<AiAdminResult, Long> {

    Optional<AiAdminResult> findFirstByRequestIdOrderByCreatedAtDesc(String requestId);

    List<AiAdminResult> findAllByCategoryOrderByCreatedAtDesc(AiAdminCategory category);

    List<AiAdminResult> findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);
}
