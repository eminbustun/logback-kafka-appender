
# Logback Kafka Appender (Modernized Fork)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.eminbustun/logback-kafka-appender.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.eminbustun%22%20AND%20a:%22logback-kafka-appender%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

This library provides a **Logback** appender that writes application logs directly to **Apache Kafka**.

## Credits & Acknowledgments

**This project is a modernized fork of the original [logback-kafka-appender](https://github.com/danielwegener/logback-kafka-appender) created by [Daniel Wegener](https://github.com/danielwegener).**

We deeply respect and appreciate Daniel's significant contribution to the open-source community. He built the robust foundation that this project stands upon. This fork aims to continue his work by updating the library for modern Java ecosystems (Java 17+, Kafka 3.x), maintaining dependencies, and adding new features requested by the community.

## Key Features & Changes in This Fork

* **Java 17+ Support:** The codebase has been modernized to support Java 17 and newer LTS versions.
* **Upgraded Dependencies:**
    * `kafka-clients` updated to **3.x** (compatible with modern brokers).
    * `logback-classic` updated to **1.5.x**.
    * `slf4j-api` updated to **2.0.x**.
* **New Partitioning Strategy:** Added `MDCKeyingStrategy` to partition logs based on specific business keys (e.g., User ID, Transaction ID).
* **Fixed Key Encoding:** Fixed issues where keys were being hashed incorrectly; now supports raw UTF-8 string keys for better observability.
* **Cleaned Up:** Removed deprecated `BlockingDeliveryStrategy` to ensure non-blocking performance.

## Installation

This library is available on **Maven Central**.

### Maven
```xml
<dependency>
    <groupId>io.github.eminbustun</groupId>
    <artifactId>logback-kafka-appender</artifactId>
    <version>1.0.0</version>
</dependency>
````

### Gradle

```groovy
implementation 'io.github.eminbustun:logback-kafka-appender:1.0.0'
```

## Configuration

Here is a complete example `logback.xml` configuration.

```xml
<configuration>

    <appender name="KAFKA" class="com.github.eminbustun.logback.kafka.KafkaAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>

        <topic>app-logs</topic>

        <keyingStrategy class="com.github.eminbustun.logback.kafka.keying.MDCKeyingStrategy">
            <mdcKey>userId</mdcKey> </keyingStrategy>

        <deliveryStrategy class="com.github.eminbustun.logback.kafka.delivery.AsynchronousDeliveryStrategy" />

        <producerConfig>bootstrap.servers=localhost:9092</producerConfig>
        <producerConfig>client.id=${HOSTNAME}</producerConfig>
        <producerConfig>compression.type=gzip</producerConfig>
        <producerConfig>max.block.ms=2000</producerConfig> </appender>

    <appender name="ASYNC_KAFKA" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="KAFKA" />
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>true</neverBlock>
    </appender>

    <root level="info">
        <appender-ref ref="ASYNC_KAFKA" />
    </root>

</configuration>
```



### Using Round-Robin Partitioning (NoKey)
If you want to distribute logs evenly across all available partitions (load balancing) without any specific ordering requirement, use the `NoKeyKeyingStrategy`. This effectively disables key generation.

**XML:**
```xml
<keyingStrategy class="com.github.eminbustun.logback.kafka.keying.NoKeyKeyingStrategy" />
````

## Partitioning Strategies (KeyingStrategy)

Partitioning is crucial for log ordering. Kafka guarantees order only within a single partition.

| Strategy | Description |
| :--- | :--- |
| **`NoKeyKeyingStrategy`** | (Default) No key is generated. Logs are distributed round-robin. Good for load balancing, bad for ordering. |
| **`HostNameKeyingStrategy`** | Uses the hostname as the key. All logs from a specific server go to the same partition. |
| **`MDCKeyingStrategy`** | **(New)** Uses a value from the Logback MDC (Mapped Diagnostic Context). Useful for keeping logs of a specific user or transaction together. |
| **`ThreadNameKeyingStrategy`** | Uses the thread name as the key. |
| **`LoggerNameKeyingStrategy`** | Uses the logger name (e.g., class name) as the key. |

### Using MDC Partitioning

To group logs by a business ID (e.g., `transactionId`), use `MDCKeyingStrategy` in XML and set the value in your Java code:

**Java:**

```java
import org.slf4j.MDC;
// ...
MDC.put("transactionId", "TX-12345");
logger.info("Processing payment..."); // This log will be keyed with "TX-12345"
MDC.remove("transactionId");
```

**XML:**

```xml
<keyingStrategy class="com.github.eminbustun.logback.kafka.keying.MDCKeyingStrategy">
    <mdcKey>transactionId</mdcKey>
</keyingStrategy>
```

## License

This project is licensed under the [Apache License Version 2.0](https://www.google.com/search?q=LICENSE).