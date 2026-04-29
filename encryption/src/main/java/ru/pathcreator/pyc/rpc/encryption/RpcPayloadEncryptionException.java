package ru.pathcreator.pyc.rpc.encryption;

public final class RpcPayloadEncryptionException extends RuntimeException {

    private RpcPayloadEncryptionException(
            final String message,
            final Throwable cause
    ) {
        super(message, cause);
    }

    public static RpcPayloadEncryptionException encryptionFailed(
            final Throwable cause
    ) {
        return new RpcPayloadEncryptionException("RPC payload encryption failed", cause);
    }

    public static RpcPayloadEncryptionException decryptionFailed(
            final Throwable cause
    ) {
        return new RpcPayloadEncryptionException("RPC payload decryption failed", cause);
    }

    public static RpcPayloadEncryptionException invalidPayload() {
        return new RpcPayloadEncryptionException("RPC payload decryption failed", null);
    }
}