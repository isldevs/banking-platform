package com.demo.notification.infrastructure.messaging;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ContainerPausingBackOffHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerContainerPauseService;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.FixedBackOff;

/** Keep failed events in Kafka until their transactional side effects can be committed. */
@Configuration
public class ConsumerRecoveryConfig {
  @Bean
  ThreadPoolTaskScheduler consumerRetryScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("consumer-retry-");
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  @Bean
  ListenerContainerPauseService consumerPauseService(
      KafkaListenerEndpointRegistry registry,
      @Qualifier("consumerRetryScheduler") ThreadPoolTaskScheduler scheduler) {
    return new ListenerContainerPauseService(registry, scheduler);
  }

  @Bean
  DefaultErrorHandler consumerErrorHandler(
      ListenerContainerPauseService pauseService,
      @Value("${messaging.consumer-retry-delay-ms:1000}") long retryDelayMillis) {
    if (retryDelayMillis < 1) {
      throw new IllegalArgumentException("messaging.consumer-retry-delay-ms must be positive");
    }
    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            (record, failure) -> {
              // Fail closed if retry policy changes later; never log-and-discard a bank event.
              throw new KafkaException(
                  "Consumer recovery must not discard an unprocessed event", failure);
            },
            new FixedBackOff(retryDelayMillis, FixedBackOff.UNLIMITED_ATTEMPTS),
            new ContainerPausingBackOffHandler(pauseService));
    // Framework fatal classifications would otherwise bypass retries and advance offsets.
    handler.setClassifications(Map.of(), true);
    handler.setAckAfterHandle(false);
    handler.setCommitRecovered(false);
    return handler;
  }
}
