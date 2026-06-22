package com.example.team3final.domain.location.repository;

import com.example.team3final.domain.location.entity.UserLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserLocationRepository extends JpaRepository<UserLocation, Long> {

    // matchId + userId 조합으로 위치 조회
    Optional<UserLocation> findByMatchIdAndUserId(Long matchId, Long userId);

    // matchId로 양측 위치 전체 조회 (등록자 + 신청자)
    List<UserLocation> findAllByMatchId(Long matchId);

    // 단체 매칭에서는 같은 게시글에 여러 matchId가 생기므로, 게시글 단위 활성 matchId들의 위치를 함께 조회합니다.
    List<UserLocation> findAllByMatchIdIn(List<Long> matchIds);

    // 매칭 완료/취소/노쇼 시 위치 데이터 전체 삭제
    void deleteAllByMatchId(Long matchId);
}
