package com.demo.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@SpringJUnitConfig(ConsumerRecoveryIntegrationTest.TestConfig.class)
@TestPropertySource(properties = "messaging.consumer-retry-delay-ms=50")
@Testcontainers(disabledWithoutDocker = true)
class ConsumerRecoveryIntegrationTest {
  private static final String COMPLETED = "transfer.completed.v1";
  private static final String FAILED = "transfer.failed.v1";
  private static final String GROUP = "recovery-test-" + UUID.randomUUID();

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("consumer_test")
          .withUsername("consumer_test")
          .withPassword("consumer_test");

  @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.1");

  @Autowired JdbcTemplate jdbc;
  @Autowired KafkaTemplate<String, String> producer;
  @Autowired DefaultErrorHandler errorHandler;
  @Autowired KafkaListenerEndpointRegistry registry;
  @Autowired ObjectMapper mapper;

  @Test
  @Timeout(180)
  void retriesPastDefaultLimitWithoutSkippingAndDeduplicatesAfterDatabaseRecovers()
      throws Exception {
    await()
        .atMost(Duration.ofSeconds(60))
        .until(
            () -> {
              Collection<MessageListenerContainer> containers = registry.getListenerContainers();
              return !containers.isEmpty()
                  && containers.stream()
                      .allMatch(
                          container ->
                              container.getAssignedPartitions() != null
                                  && container.getAssignedPartitions().size() == 2);
            });

    UUID eventId = UUID.randomUUID();
    String payload =
        mapper.writeValueAsString(
            Map.of(
                "eventId", eventId.toString(),
                "eventType", COMPLETED,
                "occurredAt", "2026-10-28T00:00:00Z",
                "transferId", UUID.randomUUID().toString(),
                "sourceAccountId", UUID.randomUUID().toString(),
                "destinationAccountId", UUID.randomUUID().toString(),
                "amount", "10.00",
                "currency", "USD",
                "requestedBy", UUID.randomUUID().toString()));
    CountDownLatch failedDeliveries = new CountDownLatch(12);
    errorHandler.setRetryListeners((record, exception, attempt) -> failedDeliveries.countDown());

    boolean databasePaused = false;
    try (AdminClient admin =
        AdminClient.create(
            Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
      try {
        // Interrupt the actual dependency, not just a mocked repository call.
        // Pausing interrupts PostgreSQL responses without replacing its published port.
        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
        databasePaused = true;
        var sent = producer.send(COMPLETED, eventId.toString(), payload).get(30, TimeUnit.SECONDS);
        TopicPartition partition =
            new TopicPartition(
                sent.getRecordMetadata().topic(), sent.getRecordMetadata().partition());
        long offset = sent.getRecordMetadata().offset();

        // Spring Kafka's default recoverer would have discarded this record after ten failures.
        assertThat(failedDeliveries.await(60, TimeUnit.SECONDS)).isTrue();
        OffsetAndMetadata committed = offsets(admin).get(partition);
        assertThat(committed == null || committed.offset() <= offset).isTrue();

        postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
        databasePaused = false;
        await()
            .atMost(Duration.ofSeconds(60))
            .ignoreExceptions()
            .untilAsserted(
                () ->
                    assertThat(
                            jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notifications WHERE event_id=?",
                                Integer.class,
                                eventId))
                        .isEqualTo(1));

        // Replaying the same durable identity must not create another side effect.
        var replay =
            producer.send(COMPLETED, eventId.toString(), payload).get(30, TimeUnit.SECONDS);
        long replayOffset = replay.getRecordMetadata().offset();
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(
                () ->
                    assertThat(offsets(admin).get(partition))
                        .isNotNull()
                        .extracting(OffsetAndMetadata::offset)
                        .isEqualTo(replayOffset + 1));
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE event_id=?", Integer.class, eventId))
            .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM processed_events WHERE event_id=?",
                    Integer.class,
                    eventId))
            .isEqualTo(1);
      } finally {
        if (databasePaused) {
          postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
        }
      }
    }
  }

  private static Map<TopicPartition, OffsetAndMetadata> offsets(AdminClient admin)
      throws Exception {
    return admin
        .listConsumerGroupOffsets(GROUP)
        .partitionsToOffsetAndMetadata()
        .get(10, TimeUnit.SECONDS);
  }

  @Configuration
  @EnableKafka
  @EnableTransactionManagement
  @Import({ConsumerRecoveryConfig.class, NotificationHandler.class})
  static class TestConfig {
    @Bean
    DataSource dataSource() {
      String url = postgres.getJdbcUrl();
      DriverManagerDataSource source =
          new DriverManagerDataSource(
              url + (url.contains("?") ? "&" : "?") + "connectTimeout=1&socketTimeout=2",
              postgres.getUsername(),
              postgres.getPassword());
      Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
      return source;
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
      return new JdbcTemplate(dataSource);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    KafkaAdmin kafkaAdmin() {
      return new KafkaAdmin(
          Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }

    @Bean
    KafkaAdmin.NewTopics transferTopics() {
      NewTopic completed = TopicBuilder.name(COMPLETED).partitions(1).replicas(1).build();
      NewTopic failed = TopicBuilder.name(FAILED).partitions(1).replicas(1).build();
      return new KafkaAdmin.NewTopics(completed, failed);
    }

    @Bean
    KafkaTemplate<String, String> producer() {
      return new KafkaTemplate<>(
          new DefaultKafkaProducerFactory<>(
              Map.of(
                  ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                  ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                  ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
        DefaultErrorHandler errorHandler) {
      ConcurrentKafkaListenerContainerFactory<String, String> factory =
          new ConcurrentKafkaListenerContainerFactory<>();
      factory.setConsumerFactory(
          new DefaultKafkaConsumerFactory<>(
              Map.of(
                  ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                  kafka.getBootstrapServers(),
                  ConsumerConfig.GROUP_ID_CONFIG,
                  GROUP,
                  ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                  false,
                  ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                  "earliest",
                  ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                  StringDeserializer.class,
                  ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                  StringDeserializer.class)));
      factory.setCommonErrorHandler(errorHandler);
      factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
      factory.getContainerProperties().setPollTimeout(100);
      return factory;
    }
  }
}
