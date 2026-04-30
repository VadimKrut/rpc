package ru.pathcreator.pyc.rpc.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("rpc.spring")
public final class RpcSpringProperties {

    private boolean enabled = true;
    private final List<String> scanPackages = new ArrayList<>();
    private final Map<String, Channel> channels = new LinkedHashMap<>();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(
            final boolean enabled
    ) {
        this.enabled = enabled;
    }

    public List<String> getScanPackages() {
        return this.scanPackages;
    }

    public Map<String, Channel> getChannels() {
        return this.channels;
    }

    public static final class Channel {

        private String publicationChannel;
        private String subscriptionChannel;
        private int streamId;
        private int fragmentLimit = 128;
        private int listenerMaxYields = 512;
        private int publisherMaxYields = 512;
        private int waitersInitialCapacity = 1_048_576;
        private float waitersLoadFactor = 0.75f;
        private long clientDefaultTimeoutNs = 5_000_000_000L;
        private boolean registerClientBeans = true;
        private final Encryption clientEncryption = new Encryption();
        private final Encryption serverEncryption = new Encryption();

        public String getPublicationChannel() {
            return this.publicationChannel;
        }

        public void setPublicationChannel(
                final String publicationChannel
        ) {
            this.publicationChannel = publicationChannel;
        }

        public String getSubscriptionChannel() {
            return this.subscriptionChannel;
        }

        public void setSubscriptionChannel(
                final String subscriptionChannel
        ) {
            this.subscriptionChannel = subscriptionChannel;
        }

        public int getStreamId() {
            return this.streamId;
        }

        public void setStreamId(
                final int streamId
        ) {
            this.streamId = streamId;
        }

        public int getFragmentLimit() {
            return this.fragmentLimit;
        }

        public void setFragmentLimit(
                final int fragmentLimit
        ) {
            this.fragmentLimit = fragmentLimit;
        }

        public int getListenerMaxYields() {
            return this.listenerMaxYields;
        }

        public void setListenerMaxYields(
                final int listenerMaxYields
        ) {
            this.listenerMaxYields = listenerMaxYields;
        }

        public int getPublisherMaxYields() {
            return this.publisherMaxYields;
        }

        public void setPublisherMaxYields(
                final int publisherMaxYields
        ) {
            this.publisherMaxYields = publisherMaxYields;
        }

        public int getWaitersInitialCapacity() {
            return this.waitersInitialCapacity;
        }

        public void setWaitersInitialCapacity(
                final int waitersInitialCapacity
        ) {
            this.waitersInitialCapacity = waitersInitialCapacity;
        }

        public float getWaitersLoadFactor() {
            return this.waitersLoadFactor;
        }

        public void setWaitersLoadFactor(
                final float waitersLoadFactor
        ) {
            this.waitersLoadFactor = waitersLoadFactor;
        }

        public long getClientDefaultTimeoutNs() {
            return this.clientDefaultTimeoutNs;
        }

        public void setClientDefaultTimeoutNs(
                final long clientDefaultTimeoutNs
        ) {
            this.clientDefaultTimeoutNs = clientDefaultTimeoutNs;
        }

        public boolean isRegisterClientBeans() {
            return this.registerClientBeans;
        }

        public void setRegisterClientBeans(
                final boolean registerClientBeans
        ) {
            this.registerClientBeans = registerClientBeans;
        }

        public Encryption getClientEncryption() {
            return this.clientEncryption;
        }

        public Encryption getServerEncryption() {
            return this.serverEncryption;
        }
    }

    public static final class Encryption {

        private Algorithm algorithm = Algorithm.NONE;
        private String keyBase64;
        private int noncePrefix;
        private long initialCounter = 1L;

        public Algorithm getAlgorithm() {
            return this.algorithm;
        }

        public void setAlgorithm(
                final Algorithm algorithm
        ) {
            this.algorithm = algorithm;
        }

        public String getKeyBase64() {
            return this.keyBase64;
        }

        public void setKeyBase64(
                final String keyBase64
        ) {
            this.keyBase64 = keyBase64;
        }

        public int getNoncePrefix() {
            return this.noncePrefix;
        }

        public void setNoncePrefix(
                final int noncePrefix
        ) {
            this.noncePrefix = noncePrefix;
        }

        public long getInitialCounter() {
            return this.initialCounter;
        }

        public void setInitialCounter(
                final long initialCounter
        ) {
            this.initialCounter = initialCounter;
        }
    }

    public enum Algorithm {
        NONE,
        AES_GCM,
        CHACHA20_POLY1305
    }
}