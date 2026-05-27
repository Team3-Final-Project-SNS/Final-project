package com.example.team3final.domain.ai.common.repository;


import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiCallMetricRepository extends JpaRepository<AiCallMetric, Long> {
    List<AiCallMetric> findAllByModelOrderByCreatedAtDesc(String model);

    List<AiCallMetric> findAllByFeatureOrderByCreatedAtDesc(AiFeature feature);

    List<AiCallMetric> findAllByStatusOrderByCreatedAtDesc(AiCallStatus status);

}
