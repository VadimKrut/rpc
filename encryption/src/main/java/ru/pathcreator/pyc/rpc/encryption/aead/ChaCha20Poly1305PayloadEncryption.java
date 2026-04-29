package ru.pathcreator.pyc.rpc.encryption.aead;

import javax.crypto.spec.IvParameterSpec;
import java.security.spec.AlgorithmParameterSpec;

public final class ChaCha20Poly1305PayloadEncryption extends AbstractAeadPayloadEncryption {

    public ChaCha20Poly1305PayloadEncryption(
            final byte[] key,
            final int noncePrefix,
            final long initialCounter
    ) {
        super(key, noncePrefix, initialCounter, "ChaCha20");
    }

    @Override
    protected void validateKey(final byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("ChaCha20-Poly1305 key must be 32 bytes");
        }
    }

    @Override
    protected String transformation() {
        return "ChaCha20-Poly1305";
    }

    @Override
    protected AlgorithmParameterSpec parameterSpec(final byte[] nonce) {
        return new IvParameterSpec(nonce);
    }
}