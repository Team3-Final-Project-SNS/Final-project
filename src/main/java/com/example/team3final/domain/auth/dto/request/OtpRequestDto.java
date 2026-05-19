package com.example.team3final.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpRequestDto(
        @NotBlank
        @Email
        String email
) {}
