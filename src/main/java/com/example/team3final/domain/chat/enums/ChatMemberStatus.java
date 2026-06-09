package com.example.team3final.domain.chat.enums;

public enum ChatMemberStatus {
    ACTIVE,   // 정상 참여 중
    NO_SHOW   // 노쇼 제한 (전송 차단, leftAt 이후 메시지 조회 불가)
}