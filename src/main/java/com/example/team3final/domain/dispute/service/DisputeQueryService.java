package com.example.team3final.domain.dispute.service;

import java.util.List;
import java.util.Set;

public interface DisputeQueryService {

    // 노쇼 확정 배치(judgeNoShowConfirmed)에서 사용
    Set<Long> getMatchIdsWithActiveDispute(List<Long> matchIds);
}
