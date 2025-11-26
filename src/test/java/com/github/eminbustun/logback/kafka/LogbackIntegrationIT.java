package com.github.eminbustun.logback.kafka;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.eminbustun.logback.kafka.delivery.AsynchronousDeliveryStrategy;
import com.github.eminbustun.logback.kafka.keying.NoKeyKeyingStrategy;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
public class LogbackIntegrationIT {

    @Container
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    private KafkaAppender<ILoggingEvent> kafkaAppender;
    private LoggerContext loggerContext;
    private static final String TOPIC = "logs";

    @BeforeEach
    public void setup() throws Exception {
        // 1. Create the topic to prevent "Leader Not Available" startup race conditions
        try (AdminClient adminClient = AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
        ))) {
            NewTopic newTopic = new NewTopic(TOPIC, 1, (short) 1);
            adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
        }

        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.reset();

        // 2. CRITICAL: Silence the Kafka internal logs.
        // Otherwise, the internal Producer logs itself to the same topic, breaking the test assertions.
        loggerContext.getLogger("org.apache.kafka").setLevel(Level.WARN);
        loggerContext.getLogger("org.apache.kafka.clients").setLevel(Level.WARN);

        // 3. Configure the Appender
        kafkaAppender = new KafkaAppender<>();
        kafkaAppender.setContext(loggerContext);
        kafkaAppender.setName("kafkaAppender");
        kafkaAppender.setTopic(TOPIC);

        kafkaAppender.addProducerConfigValue("bootstrap.servers", kafka.getBootstrapServers());
        kafkaAppender.addProducerConfigValue("client.id", "integration-test-client");
        kafkaAppender.addProducerConfigValue("max.block.ms", "10000");

        kafkaAppender.setKeyingStrategy(new NoKeyKeyingStrategy());
        kafkaAppender.setDeliveryStrategy(new AsynchronousDeliveryStrategy());

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%msg");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();
        kafkaAppender.setEncoder(encoder);

        kafkaAppender.start();
    }

    @AfterEach
    public void tearDown() {
        if (kafkaAppender != null) {
            kafkaAppender.stop();
        }
    }

    @Test
    public void testLoggingToKafka() {
        Logger logger = loggerContext.getLogger("ROOT");
        logger.addAppender(kafkaAppender);
        logger.setLevel(Level.INFO);

        int messageCount = 10;

        for (int i = 0; i < messageCount; ++i) {
            logger.info("message-" + i);
        }

        try (KafkaConsumer<byte[], byte[]> consumer = createConsumer()) {
            consumer.assign(Collections.singletonList(new TopicPartition(TOPIC, 0)));
            consumer.seekToBeginning(Collections.singletonList(new TopicPartition(TOPIC, 0)));

            int readMessages = 0;
            long deadline = System.currentTimeMillis() + 10000;

            while (readMessages < messageCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    String message = new String(record.value(), StandardCharsets.UTF_8);

                    // Robustness: Skip internal logs if they still sneak in
                    if (!message.startsWith("message-")) {
                        System.err.println("Skipping unexpected log message in topic: " + message);
                        continue;
                    }

                    assertEquals("message-" + readMessages, message);
                    readMessages++;
                }
            }

            assertEquals(messageCount, readMessages, "Did not receive all messages from Kafka");
        }
    }

    private KafkaConsumer<byte[], byte[]> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }
}
