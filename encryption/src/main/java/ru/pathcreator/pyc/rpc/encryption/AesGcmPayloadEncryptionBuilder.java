package ru.pathcreator.pyc.rpc.encryption;

import ru.pathcreator.pyc.rpc.encryption.aead.AesGcmPayloadEncryption;

public final class AesGcmPayloadEncryptionBuilder {

    private byte[] key;
    private boolean keyConfigured;
    private int noncePrefix;
    private long initialCounter;

    AesGcmPayloadEncryptionBuilder() {
    }

    public AesGcmPayloadEncryptionBuilder key(
            final byte[] key
    ) {
        return this.key(key, false);
    }

    public AesGcmPayloadEncryptionBuilder key(
            final byte[] key,
            final boolean wipeSource
    ) {
        this.key = copyKey(key);
        this.keyConfigured = true;
        if (wipeSource) {
            java.util.Arrays.fill(key, (byte) 0);
        }
        return this;
    }

    public AesGcmPayloadEncryptionBuilder noncePrefix(
            final int noncePrefix
    ) {
        this.noncePrefix = noncePrefix;
        return this;
    }

    public AesGcmPayloadEncryptionBuilder initialCounter(
            final long initialCounter
    ) {
        this.initialCounter = initialCounter;
        return this;
    }

    public RpcPayloadEncryption build() {
        if (!this.keyConfigured) {
            throw new IllegalStateException("key must be configured");
        }
        return new AesGcmPayloadEncryption(this.key, this.noncePrefix, this.initialCounter);
    }

    private static byte[] copyKey(
            final byte[] key
    ) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return key.clone();
    }
}