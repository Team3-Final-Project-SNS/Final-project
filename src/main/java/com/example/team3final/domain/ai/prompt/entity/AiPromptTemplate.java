package com.example.team3final.domain.ai.prompt.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.enums.AiPromptType;
import jakarta.persistence.*;
import lombok.*;


/**
 * AI 프롬프트 템플릿 메타데이터 엔티티입니다.
 *
 * 실제 프롬프트 본문은 외부 파일 또는 classpath 리소스에 두고,
 * 이 엔티티는 어떤 AI 기능이 어떤 프롬프트 파일과 버전을 사용할지 관리합니다.
 *
 * 이를 통해 프롬프트 파일명, 활성 버전, 기능 구분을 DB에서 관리할 수 있으며,
 * 코드에 프롬프트 본문을 하드코딩하지 않는 구조를 제공합니다.
 */

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_prompt_templates",
        indexes = {
                @Index(name = "idx_ai_prompt_type_active", columnList = "prompt_type, active")
        }
)
public class AiPromptTemplate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 프롬프트 용도입니다.
     * 예: MATCHING_CHAT, SUPPORT_CHAT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "prompt_type", nullable = false, length = 40)
    private AiPromptType promptType;

    /**
     * 프롬프트가 속한 AI 기능입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    /**
     * 프롬프트 버전입니다.
     * 예: v1, v2, v3
     */
    @Column(nullable = false, length = 30)
    private String version;

    /**
     * 외부 프롬프트 파일명입니다.
     * base-path와 조합해서 실제 파일을 읽습니다.
     * 예: matching-chat-v2.st
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * 현재 활성화된 프롬프트인지 여부입니다.
     */
    @Column(nullable = false)
    private boolean active;

    /**
     * 프롬프트 설명 또는 적용 목적입니다.
     */
    @Column(length = 500)
    private String description;

    /**
     * 프롬프트 교체 시 활성 상태를 변경합니다.
     */
    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
