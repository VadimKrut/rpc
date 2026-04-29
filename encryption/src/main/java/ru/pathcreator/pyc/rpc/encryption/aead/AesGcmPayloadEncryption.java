package ru.pathcreator.pyc.rpc.encryption.aead;

import javax.crypto.spec.GCMParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

public final class AesGcmPayloadEncryption extends AbstractAeadPayloadEncryption {

    public AesGcmPayloadEncryption(
            final byte[] key,
            final int noncePrefix,
            final long initialCounter
    ) {
        super(key, noncePrefix, initialCounter, "AES");
    }

    @Override
    protected void validateKey(final byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        final int length = key.length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalArgumentException("AES-GCM key must be 16, 24 or 32 bytes");
        }
    }

    @Override
    protected String transformation() {
        return "AES/GCM/NoPadding";
    }

    @Override
    protected AlgorithmParameterSpec parameterSpec(final byte[] nonce) {
        return new GCMParameterSpec(128, nonce);
    }
}