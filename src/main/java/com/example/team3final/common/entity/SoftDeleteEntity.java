package com.example.team3final.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass
public abstract class SoftDeleteEntity extends BaseUpdateEntity {

    @Column(name = "deleted_at") // 초기값은 null이며, 삭제(취소/비활성화) 시점에 시각이 입력됩니다.
    private LocalDateTime deletedAt;

    // Soft Delete를 수행하는 공통 비즈니스 메서드
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    // 현재 데이터가 삭제된 상태인지 확인하는 공통 메서드
    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}