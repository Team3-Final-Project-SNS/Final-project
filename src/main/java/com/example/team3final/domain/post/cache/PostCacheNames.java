package com.example.team3final.domain.post.cache;

// 게시글 도메인에서 사용하는 캐시 이름을 관리
public class PostCacheNames {

    private PostCacheNames() {

    }

    // 게시글 목록 조회 전에 반복 조회되는 같은 대학교 유저 ID 목록 캐시 키
    public static final String SAME_UNIVERSITY_USER_IDS = "post:sameUniversityUserIds";
}
