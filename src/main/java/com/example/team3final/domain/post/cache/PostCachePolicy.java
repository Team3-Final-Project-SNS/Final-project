package com.example.team3final.domain.post.cache;

import java.time.Duration;

// 게시글 도메인에서 사용하는 캐시 이름을 관리
public class PostCachePolicy {

    private PostCachePolicy() {

    }

    // 게시글 목록 조회 캐시 키
    // Redis에는 이 cacheName을 prefix로 사용하여 key 생성
    // ex) post:list::user:8:status:OPEN:page:0:size:20
    public static final String POST_LIST = "post:list";

    // 게시글 목록 조회 TTL : 30초로 설정
    // 게시글 목록은 조회 빈도가 높지만, 게시글 등록, 모집 상태 변경, 신청자 수 변경 등에 따라 결과가 달라질 수 있음,
    // 따라서 너무 긴 TTL을 두면 오래된 목록이 노출될 수 있으므로, 30초로 짧게 선정
    public static final Duration POST_LIST_TTL = Duration.ofSeconds(30);

}
