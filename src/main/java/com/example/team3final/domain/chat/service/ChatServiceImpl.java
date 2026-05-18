package com.example.team3final.domain.chat.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.chat.entity.ChatRoom;
import com.example.team3final.domain.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    @Transactional
    @Override
    public void createChatRoom(Long matchId) {
        // 이미 채팅방이 있으면 생성 안 함
        if (chatRoomRepository.findByMatchId(matchId).isPresent()) {
            throw new ServiceException(ErrorCode.CHAT_ROOM_ALREADY_EXISTS);
        }
        ChatRoom chatRoom = ChatRoom.builder()
                .matchId(matchId)
                .build();
        chatRoomRepository.save(chatRoom);
    }
}