package com.example.team3final.domain.post.enums;

public enum PostStatus {
    // 모집 중 - 매칭가능
    OPEN,

    // 매칭 완료 - 신청자가 매칭되어 더 이상 신청 불가
    MATCHED,

    // 만남 정상 완료
    COMPLETED,

    // 취소됨 - 작성자 삭제 또는 매칭 취소
    CANCELLED
}
