package com.example.team3final;

import com.example.team3final.domain.chat.scheduler.ChatRoomScheduler;
import com.example.team3final.domain.dispute.scheduler.DisputeScheduler;
import com.example.team3final.domain.meet.scheduler.ExtensionTimeoutScheduler;
import com.example.team3final.domain.meet.scheduler.MeetReminderScheduler;
import com.example.team3final.domain.meet.scheduler.NoShowScheduler;
import com.example.team3final.domain.notification.scheduler.NotificationScheduler;
import com.example.team3final.domain.post.scheduler.PostExpiredScheduler;
import com.example.team3final.domain.review.scheduler.ReviewDeadlineReminderScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("test")
public class TestSchedulerMockConfig {

    @Bean
    public ChatRoomScheduler chatRoomScheduler() {
        return mock(ChatRoomScheduler.class);
    }

    @Bean
    public DisputeScheduler disputeScheduler() {
        return mock(DisputeScheduler.class);
    }

    @Bean
    public ExtensionTimeoutScheduler extensionTimeoutScheduler() {
        return mock(ExtensionTimeoutScheduler.class);
    }

    @Bean
    public MeetReminderScheduler meetReminderScheduler() {
        return mock(MeetReminderScheduler.class);
    }

    @Bean
    public NoShowScheduler noShowScheduler() {
        return mock(NoShowScheduler.class);
    }

    @Bean
    public NotificationScheduler notificationScheduler() {
        return mock(NotificationScheduler.class);
    }

    @Bean
    public PostExpiredScheduler postExpiredScheduler() {
        return mock(PostExpiredScheduler.class);
    }

    @Bean
    public ReviewDeadlineReminderScheduler reviewDeadlineReminderScheduler() {
        return mock(ReviewDeadlineReminderScheduler.class);
    }
}
