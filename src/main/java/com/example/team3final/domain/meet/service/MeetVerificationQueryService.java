package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.dto.response.GetMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.NoShowMatchResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;

import java.util.List;

// MeetVerification 도메인의 조회 기능을 담당하는 서비스
public interface MeetVerificationQueryService {

    QrResponseDto getMeetQrByPost(Long userId, Long postId);

    MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId);

    GetMeetExtensionResponseDto getMeetExtension(Long userId, Long matchId);

    List<NoShowMatchResponseDto> getNoShowMatchesForUser(Long userId);
}
