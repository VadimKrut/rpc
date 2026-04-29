package ru.pathcreator.pyc.rpc.encryption;

public final class RpcPayloadEncryptions {

    private RpcPayloadEncryptions() {
    }

    public static AesGcmPayloadEncryptionBuilder aesGcm() {
        return new AesGcmPayloadEncryptionBuilder();
    }

    public static RpcPayloadEncryption aesGcm(
            final byte[] key,
            final int noncePrefix,
            final long initialCounter
    ) {
        return aesGcm()
                .key(key)
                .noncePrefix(noncePrefix)
                .initialCounter(initialCounter)
                .build();
    }

    public static ChaCha20Poly1305PayloadEncryptionBuilder chaCha20Poly1305() {
        return new ChaCha20Poly1305PayloadEncryptionBuilder();
    }

    public static RpcPayloadEncryption chaCha20Poly1305(
            final byte[] key,
            final int noncePrefix,
            final long initialCounter
    ) {
        return chaCha20Poly1305()
                .key(key)
                .noncePrefix(noncePrefix)
                .initialCounter(initialCounter)
                .build();
    }
}