package com.example.team3final.domain.notification.repository;

import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 목록 조회 - 전체 (최신순)
    Page<Notification> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    // 알림 목록 조회 - 읽음 여부 필터
    Page<Notification> findByReceiverIdAndIsReadOrderByCreatedAtDesc(Long receiverId, boolean isRead, Pageable pageable);

    // 알림 목록 조회 - 유형 필터
    Page<Notification> findByReceiverIdAndTypeOrderByCreatedAtDesc(Long receiverId, NotificationType type, Pageable pageable);

    // 알림 목록 조회 - 읽음 여부 + 유형 필터
    Page<Notification> findByReceiverIdAndIsReadAndTypeOrderByCreatedAtDesc(Long receiverId, boolean isRead,
                                                                            NotificationType type, Pageable pageable);

    // 단건 조회 (본인 알림만)
    Optional<Notification> findByIdAndReceiverId(Long id, Long receiverId);

    // 미확인 알림 카운트
    long countByReceiverIdAndIsRead(Long receiverId, boolean isRead);

    // 전체 읽음 처리 (벌크 업데이트)
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.receiverId = :receiverId AND n.isRead = false")
    int markAllAsRead(@Param("receiverId") Long receiverId, @Param("now") LocalDateTime now);

    // 10일 경과 알림 삭제 (스케줄러용)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    void deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

}
