package com.example.team3final;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Configuration
@Profile("test")
public class TestRedisMockConfig {

    @Bean
    public RedissonClient redissonClient() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        when(redissonClient.getLock(anyString())).thenAnswer(invocation -> {
            String lockName = invocation.getArgument(0);
            ReentrantLock delegate = locks.computeIfAbsent(lockName, ignored -> new ReentrantLock());
            RLock lock = mock(RLock.class);

            when(lock.tryLock(
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.anyLong(),
                    org.mockito.ArgumentMatchers.any(TimeUnit.class)
            )).thenAnswer(lockInvocation -> {
                long waitTime = lockInvocation.getArgument(0);
                TimeUnit timeUnit = lockInvocation.getArgument(2);
                return delegate.tryLock(waitTime, timeUnit);
            });
            when(lock.isHeldByCurrentThread()).thenAnswer(ignored -> delegate.isHeldByCurrentThread());
            org.mockito.Mockito.doAnswer(ignored -> {
                delegate.unlock();
                return null;
            }).when(lock).unlock();

            return lock;
        });

        return redissonClient;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }
}
