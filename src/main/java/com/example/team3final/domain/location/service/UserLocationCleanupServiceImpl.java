package com.example.team3final.domain.location.service;

import com.example.team3final.domain.location.repository.UserLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserLocationCleanupServiceImpl implements UserLocationCleanupService {

    private final UserLocationRepository userLocationRepository;

    @Override
    @Transactional
    public void deleteLocationsByMatchId(Long matchId) {

        // 위치 정보는 장소 인증 화면에서만 쓰는 임시 데이터,
        // 매칭 종료되면 즉시 삭제
        userLocationRepository.deleteAllByMatchId(matchId);
    }
}
