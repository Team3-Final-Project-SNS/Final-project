package com.example.team3final.domain.meet.context;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.post.dto.response.PostInfoDto;

public record MeetVerificationContext (

        MeetVerification meetVerification,
        MatchInfoDto matchInfo,
        PostInfoDto postInfo
) {}
