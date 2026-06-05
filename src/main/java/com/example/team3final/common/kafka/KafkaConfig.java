package com.example.team3final.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConfig {

    // ==================== Consumer 실패 대응 설정 ====================

    // DeadLetterPublishingRecoverer
    // Consumer가 메시지 처리를 최종 실패했을 때 DLT 토픽으로 보내는 복구 전략
    // Spring Kafka가 자동으로 "{원본토픽}.DLT" 토픽으로 발행
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate) {

        // DLT 토픽 이름을 명시적으로 지정
        // 기본값: 원본토픽 + ".DLT" → 우리가 정의한 상수와 일치시킴
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> {
                    // 어떤 토픽에서 실패했는지에 따라 DLT 토픽 결정
                    String dltTopic = record.topic() + ".DLT";

                    log.error("[Kafka DLT] Consumer 처리 최종 실패 → DLT 발행" +
                                    " - topic: {}, partition: {}, offset: {}, error: {}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            exception.getMessage());

                    // DLT 토픽의 파티션 0으로 고정 발행
                    // DLT는 순서 보장보다 유실 방지가 목적이므로 파티션 0으로 통일
                    return new TopicPartition(dltTopic, 0);
                });
    }

    // DefaultErrorHandler
    // Consumer 예외 발생 시 재시도 횟수와 간격을 설정
    // 재시도 소진 후 DeadLetterPublishingRecoverer로 DLT에 발행
    @Bean
    public DefaultErrorHandler defaultErrorHandler(
            DeadLetterPublishingRecoverer recoverer) {

        // FixedBackOff: 고정 간격으로 재시도
        // 1번째 파라미터: 재시도 간격 (ms) - 1초
        // 2번째 파라미터: 최대 재시도 횟수 - 3회
        // 즉, 실패 시 1초 간격으로 3번 재시도 → 그래도 실패 시 DLT 발행
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // 재시도 시작 로그
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[Kafka Consumer] 메시지 처리 실패 재시도 - " +
                                "topic: {}, attempt: {}/{}, error: {}",
                        record.topic(),
                        deliveryAttempt,
                        backOff.getMaxAttempts() + 1,
                        ex.getMessage())
        );

        return errorHandler;
    }

    // ConcurrentKafkaListenerContainerFactory
    // @KafkaListener가 사용할 컨테이너 팩토리
    // DefaultErrorHandler를 주입해서 실패 대응 전략 적용
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        // ConsumerFactory: application.yml의 Kafka Consumer 설정을 읽어서 생성된 빈
        factory.setConsumerFactory(consumerFactory);

        // DefaultErrorHandler 적용 - 재시도 + DLT 발행 전략
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}