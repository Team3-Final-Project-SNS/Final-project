package com.example.team3final.domain.post.cache;

import java.time.Duration;

// 게시글 도메인에서 사용하는 캐시 이름을 관리
public class PostCachePolicy {

    private PostCachePolicy() {

    }

    // 게시글 목록 조회 전에 반복 조회되는 같은 대학교 유저 ID 목록 캐시 키
    public static final String SAME_UNIVERSITY_USER_IDS = "post:sameUniversityUserIds";

    // 같은 대학교 유저 ID 목록 캐시 TTL
    // TTL : 10분 -> 너무 짧으면 캐시 효과가 약해짐, 너무 길면 유저 상태 변경이 게시글 노출 범위에 늦게 반영될 수 있음
    // 10분은 조회 성능 개선과 데이터 최신성 사이의 절충 값
    public static final Duration SAME_UNIVERSITY_USER_IDS_TTL = Duration.ofMinutes(10);

}
