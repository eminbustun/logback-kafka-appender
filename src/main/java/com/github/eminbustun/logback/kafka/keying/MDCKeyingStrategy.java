package com.github.eminbustun.logback.kafka.keying;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.nio.charset.StandardCharsets;

/**
 * MDC (Mapped Diagnostic Context) içindeki belirli bir anahtarın değerini
 * Kafka mesaj anahtarı (Key) olarak kullanır.
 * * Bu strateji, aynı MDC değerine sahip (örneğin aynı 'siparisId' veya 'userId')
 * logların Kafka'da aynı partition'a gitmesini garanti eder.
 */
public class MDCKeyingStrategy implements KeyingStrategy<ILoggingEvent> {

    private String mdcKey;

    /**
     * logback.xml üzerinden set edilecek parametre.
     * Örnek: <mdcKey>userId</mdcKey>
     */
    public void setMdcKey(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    @Override
    public byte[] createKey(ILoggingEvent e) {
        if (mdcKey == null) return null;

        // Log olayının MDC haritasından değeri çek
        String value = e.getMDCPropertyMap().get(mdcKey);

        if (value == null) {
            return null; // Değer yoksa null döner (Random partition)
        }

        // Değeri byte dizisine çevirip Kafka'ya veriyoruz
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
