package com.example.team3final.domain.auth.dto.request;

import com.example.team3final.domain.user.dto.response.TermAgreementDto;
import com.example.team3final.domain.user.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

// 회원가입 요청 DTO
@Getter
@NoArgsConstructor
public class SignupRequestDto {

    // 비밀번호: 8자 이상 (8자 이상 영문/숫자 조합)
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하여야 합니다.")
    private String password;

    // 실명
    @NotBlank(message = "이름은 필수입니다.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    private String name;

    // 닉네임: 게시글에 표시될 이름
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 30, message = "닉네임은 2자 이상 30자 이하여야 합니다.")
    private String nickname;

    // 학과
    @NotBlank(message = "학과는 필수입니다.")
    @Size(max = 100, message = "학과는 100자 이하여야 합니다.")
    private String major;

    // 학번 (입학연도 기준 2자리, 예: "24")
    @NotBlank(message = "학번은 필수입니다.")
    @Size(max = 20, message = "학번은 20자 이하여야 합니다.")
    private String studentNumber;

    // 생년월일
    @NotNull(message = "생년월일은 필수입니다.")
    private LocalDate birthDate;

    // 성별 ENUM: MALE / FEMALE
    @NotNull(message = "성별은 필수입니다.")
    private Gender gender;

    // 약관 동의 목록: 필수 약관(서비스, 개인정보)이 포함되어야 함
    // @Valid: 리스트 내부 TermAgreementDto의 검증도 실행
    @NotNull(message = "약관 동의는 필수입니다.")
    @Valid
    private List<TermAgreementDto> termAgreements;

    @Builder
    private SignupRequestDto(String password, String name, String nickname,
                             String major, String studentNumber, LocalDate birthDate,
                             Gender gender, List<TermAgreementDto> termAgreements) {
        this.password = password;
        this.name = name;
        this.nickname = nickname;
        this.major = major;
        this.studentNumber = studentNumber;
        this.birthDate = birthDate;
        this.gender = gender;
        this.termAgreements = termAgreements;
    }
}