package com.github.eminbustun.logback.kafka.keying;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.LifeCycle;

import java.nio.charset.StandardCharsets;

public class HostNameKeyingStrategy extends ContextAwareBase implements KeyingStrategy<Object>, LifeCycle {

    private byte[] hostnameBytes = null;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        final String hostname = context.getProperty(CoreConstants.HOSTNAME_KEY);
        if (hostname == null) {
            addError("Hostname could not be found in context. HostNameKeyingStrategy will not work.");
        } else {
            // ESKİ KOD: hostnameHash = ByteBuffer.allocate(4).putInt(hostname.hashCode()).array();

            // YENİ KOD: Hostname'i direkt UTF-8 byte dizisi olarak alıyoruz.
            hostnameBytes = hostname.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public byte[] createKey(Object e) {
        return hostnameBytes;
    }

    @Override
    public void start() { }

    @Override
    public void stop() { }

    @Override
    public boolean isStarted() { return true; }
}
