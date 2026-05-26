package com.example.team3final.domain.ai.prompt.entity;

import com.example.team3final.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "ai_prompt_histories")
public class AiPromptHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 개선 이력이 연결된 프롬프트 템플릿 ID입니다.
     */
    @Column(nullable = false)
    private Long promptTemplateId;

    /**
     * 변경된 프롬프트 버전입니다.
     */
    @Column(nullable = false, length = 30)
    private String version;

    /**
     * 변경 동기입니다.
     */
    @Column(nullable = false, length = 500)
    private String changeReason;

    /**
     * 토큰 변화입니다.
     * 예: 평균 completion token 620 -> 480
     */
    @Column(length = 300)
    private String tokenChange;

    /**
     * 출력 품질 변화입니다.
     */
    @Column(length = 500)
    private String qualityChange;

    /**
     * 다음 개선 방향입니다.
     */
    @Column(length = 500)
    private String nextDirection;
}