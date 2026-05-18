package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetVerificationQueryService {
    // 조회 전용 서비스

    private final MeetVerificationRepository meetVerificationRepository;

    // matchId 조회
    public MeetVerification getByMatchId(Long matchId) {
        return meetVerificationRepository.findByMatchId(matchId)
                //TODO Match 에러코드 생성되면 적용
                .orElseThrow( () -> new IllegalArgumentException(""));
    }
}
