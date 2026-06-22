package com.example.team3final.domain.post.service;

import java.time.LocalDateTime;

// 스케줄러가 호출하는 Post 단건 만료/매칭 전환 서비스
public interface PostExpirationService {

    void process(Long postId, LocalDateTime now);
}
