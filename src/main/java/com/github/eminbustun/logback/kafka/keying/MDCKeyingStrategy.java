package com.github.eminbustun.logback.kafka.keying;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.charset.StandardCharsets;

/**
 * Uses the value of a specific key from the MDC (Mapped Diagnostic Context) as the Kafka message key.
 * This ensures that all log messages with the same MDC key (e.g. same 'userId' or 'requestId')
 * will be sent to the same Kafka partition and remain in the correct order.
 *
 * Example: {@code <mdcKey>userId</mdcKey>}
 */
public class MDCKeyingStrategy implements KeyingStrategy<ILoggingEvent> {

    private String mdcKey;

    /**
     * The parameter to be set via logback.xml.
     * Example: {@code <mdcKey>userId</mdcKey>}
     *
     * @param mdcKey The key to look up in the MDC.
     */
    public void setMdcKey(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    @Override
    public byte[] createKey(ILoggingEvent e) {
        if (mdcKey == null) return null;

        // Get the value from the log event's MDC map
        String value = e.getMDCPropertyMap().get(mdcKey);

        if (value == null) {
            return null; // Return null (Random partition) if value is missing
        }

        // Convert the value to bytes and use it as the Kafka key
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
