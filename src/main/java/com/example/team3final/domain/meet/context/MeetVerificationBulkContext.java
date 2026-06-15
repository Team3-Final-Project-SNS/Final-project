package com.example.team3final.domain.meet.context;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.post.dto.response.PostInfoDto;

import java.util.Map;

public record MeetVerificationBulkContext (

        Map<Long, MatchInfoDto> matchInfoMap,
        Map<Long, PostInfoDto> postInfoMap
) {}
