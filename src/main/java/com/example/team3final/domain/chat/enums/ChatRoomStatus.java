package com.example.team3final.domain.chat.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 채팅방 상태 enum
@Getter
@RequiredArgsConstructor
public enum ChatRoomStatus {

    ACTIVE("활성"),           // 정상 운영 중 (메시지 송수신 가능)
    READ_ONLY("읽기 전용"),    // 읽기 전용 (만남 완료 후 2시간 이내 / 노쇼 확정 — 메시지 조회만 가능)
    DEACTIVATED("비활성화");   // 완전 비활성화 (매칭 취소 — 조회 불가)

    private final String description;
}