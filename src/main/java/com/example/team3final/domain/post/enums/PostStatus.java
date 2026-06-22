package com.example.team3final.domain.post.enums;

import lombok.Getter;

@Getter
public enum PostStatus {

    OPEN("모집 중"),        // 매칭 가능 - 신청을 받을 수 있는 상태
    MATCHED("매칭 완료"),   // 신청자가 매칭되어 더 이상 신청 불가
    COMPLETED("만남 완료"), // 만남이 정상적으로 끝난 상태
    CANCELLED("취소됨"),    // 작성자 삭제 또는 매칭 취소
    EXPIRED("만료됨");      // meetAt 시각이 지났는데 매칭이 성사되지 않은 게시글

    private final String description;

    PostStatus(String description) {
        this.description = description;
    }
}
