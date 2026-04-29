package ru.pathcreator.pyc.rpc.encryption;

import ru.pathcreator.pyc.rpc.encryption.aead.ChaCha20Poly1305PayloadEncryption;

public final class ChaCha20Poly1305PayloadEncryptionBuilder {

    private byte[] key;
    private int noncePrefix;
    private boolean keyConfigured;
    private long initialCounter;

    ChaCha20Poly1305PayloadEncryptionBuilder() {
    }

    public ChaCha20Poly1305PayloadEncryptionBuilder key(
            final byte[] key
    ) {
        return this.key(key, false);
    }

    public ChaCha20Poly1305PayloadEncryptionBuilder key(
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

    public ChaCha20Poly1305PayloadEncryptionBuilder noncePrefix(
            final int noncePrefix
    ) {
        this.noncePrefix = noncePrefix;
        return this;
    }

    public ChaCha20Poly1305PayloadEncryptionBuilder initialCounter(
            final long initialCounter
    ) {
        this.initialCounter = initialCounter;
        return this;
    }

    public RpcPayloadEncryption build() {
        if (!this.keyConfigured) {
            throw new IllegalStateException("key must be configured");
        }
        return new ChaCha20Poly1305PayloadEncryption(this.key, this.noncePrefix, this.initialCounter);
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