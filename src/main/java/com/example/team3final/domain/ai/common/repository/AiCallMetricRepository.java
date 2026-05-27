package com.example.team3final.domain.ai.common.repository;


import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * AI 호출 메트릭을 조회하고 저장하는 Repository입니다.
 *
 * 매칭 AI, 고객지원 AI, 신고 요약 AI 등에서 발생한 LLM 호출 결과를
 * AiCallMetric 엔티티로 저장하여 토큰 사용량, 응답 지연 시간,
 * 성공/실패 상태, 에러 유형을 추적하는 데 사용합니다.
 *
 * 현재 매칭 AI에서는 호출 성공/실패 시점에 metric을 저장하고,
 * 조회 메서드는 추후 관리자 대시보드 또는 운영 모니터링 API에서
 * 모델별/기능별/상태별 메트릭을 확인할 때 사용합니다.
 */

public interface AiCallMetricRepository extends JpaRepository<AiCallMetric, Long> {
    List<AiCallMetric> findAllByModelOrderByCreatedAtDesc(String model);

    List<AiCallMetric> findAllByFeatureOrderByCreatedAtDesc(AiFeature feature);

    List<AiCallMetric> findAllByStatusOrderByCreatedAtDesc(AiCallStatus status);

}
