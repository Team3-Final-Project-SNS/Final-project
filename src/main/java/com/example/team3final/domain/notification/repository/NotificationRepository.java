package com.example.team3final.domain.notification.repository;

import com.example.team3final.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 알림 목록 조회 - 전체 (커서 기반, 최신순)
    // cursorId 미만 알림만 조회 (cursorId = Long.MAX_VALUE 이면 처음부터)
    @Query("SELECT n FROM Notification n WHERE n.receiverId = :receiverId AND n.id < :cursorId ORDER BY n.id DESC")
    List<Notification> findByReceiverIdAndIdLessThan(
            @Param("receiverId") Long receiverId,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    // 미확인 알림 카운트
    long countByReceiverIdAndIsRead(Long receiverId, boolean isRead);

    // 전체 읽음 처리 (벌크 업데이트)
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.receiverId = :receiverId AND n.isRead = false")
    int markAllAsRead(@Param("receiverId") Long receiverId, @Param("now") LocalDateTime now);

    // 10일 경과 알림 삭제 (스케줄러용)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n IN (SELECT n2 FROM Notification n2 WHERE n2.createdAt < :cutoff ORDER BY n2.createdAt ASC LIMIT :limit)")
    int deleteByCreatedAtBeforeLimit(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

}

