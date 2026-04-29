package ru.pathcreator.pyc.rpc.encryption.aead;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryptionException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.concurrent.atomic.AtomicLong;

abstract class AbstractAeadPayloadEncryption implements RpcPayloadEncryption {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    private final SecretKey key;
    private final int noncePrefix;
    private final AtomicLong counter;
    private final ThreadLocal<CipherState> states;

    AbstractAeadPayloadEncryption(
            final byte[] key,
            final int noncePrefix,
            final long initialCounter,
            final String keyAlgorithm
    ) {
        validateKey(key);
        this.key = new SecretKeySpec(key.clone(), keyAlgorithm);
        this.noncePrefix = noncePrefix;
        this.counter = new AtomicLong(initialCounter);
        this.states = ThreadLocal.withInitial(this::newState);
    }

    @Override
    public final int maxEncryptedLength(final int plainLength) {
        if (plainLength < 0) {
            throw new IllegalArgumentException("plainLength must be >= 0");
        }
        return NONCE_LENGTH + plainLength + TAG_LENGTH;
    }

    @Override
    public final int encrypt(
            final DirectBuffer source,
            final int sourceOffset,
            final int sourceLength,
            final MutableDirectBuffer target,
            final int targetOffset
    ) {
        final CipherState state = this.states.get();
        state.ensureInputCapacity(sourceLength);
        source.getBytes(sourceOffset, state.input, 0, sourceLength);
        encodeNonce(state.nonce, this.noncePrefix, this.counter.getAndIncrement());
        try {
            state.encrypt.init(Cipher.ENCRYPT_MODE, this.key, parameterSpec(state.nonce));
            state.ensureOutputCapacity(state.encrypt.getOutputSize(sourceLength));
            final int encryptedLength = state.encrypt.doFinal(
                    state.input,
                    0,
                    sourceLength,
                    state.output,
                    0
            );
            target.putBytes(targetOffset, state.nonce, 0, NONCE_LENGTH);
            target.putBytes(targetOffset + NONCE_LENGTH, state.output, 0, encryptedLength);
            return NONCE_LENGTH + encryptedLength;
        } catch (final GeneralSecurityException error) {
            throw RpcPayloadEncryptionException.encryptionFailed(error);
        }
    }

    @Override
    public final int decrypt(
            final DirectBuffer source,
            final int sourceOffset,
            final int sourceLength,
            final MutableDirectBuffer target,
            final int targetOffset
    ) {
        if (sourceLength < NONCE_LENGTH + TAG_LENGTH) {
            throw RpcPayloadEncryptionException.invalidPayload();
        }
        final CipherState state = this.states.get();
        final int encryptedLength = sourceLength - NONCE_LENGTH;
        source.getBytes(sourceOffset, state.nonce, 0, NONCE_LENGTH);
        state.ensureInputCapacity(encryptedLength);
        source.getBytes(sourceOffset + NONCE_LENGTH, state.input, 0, encryptedLength);
        try {
            state.decrypt.init(Cipher.DECRYPT_MODE, this.key, parameterSpec(state.nonce));
            state.ensureOutputCapacity(state.decrypt.getOutputSize(encryptedLength));
            final int plainLength = state.decrypt.doFinal(
                    state.input,
                    0,
                    encryptedLength,
                    state.output,
                    0
            );
            target.putBytes(targetOffset, state.output, 0, plainLength);
            return plainLength;
        } catch (final GeneralSecurityException error) {
            throw RpcPayloadEncryptionException.decryptionFailed(error);
        }
    }

    protected abstract void validateKey(byte[] key);

    protected abstract String transformation();

    protected abstract AlgorithmParameterSpec parameterSpec(byte[] nonce);

    private CipherState newState() {
        try {
            return new CipherState(
                    Cipher.getInstance(this.transformation()),
                    Cipher.getInstance(this.transformation())
            );
        } catch (final GeneralSecurityException error) {
            throw new IllegalStateException("Unable to initialize payload cipher", error);
        }
    }

    private static void encodeNonce(
            final byte[] nonce,
            final int prefix,
            final long counter
    ) {
        nonce[0] = (byte) (prefix >>> 24);
        nonce[1] = (byte) (prefix >>> 16);
        nonce[2] = (byte) (prefix >>> 8);
        nonce[3] = (byte) prefix;
        nonce[4] = (byte) (counter >>> 56);
        nonce[5] = (byte) (counter >>> 48);
        nonce[6] = (byte) (counter >>> 40);
        nonce[7] = (byte) (counter >>> 32);
        nonce[8] = (byte) (counter >>> 24);
        nonce[9] = (byte) (counter >>> 16);
        nonce[10] = (byte) (counter >>> 8);
        nonce[11] = (byte) counter;
    }

    private static final class CipherState {

        private final Cipher encrypt;
        private final Cipher decrypt;
        private final byte[] nonce = new byte[NONCE_LENGTH];
        private byte[] input = new byte[128];
        private byte[] output = new byte[160];

        private CipherState(
                final Cipher encrypt,
                final Cipher decrypt
        ) {
            this.encrypt = encrypt;
            this.decrypt = decrypt;
        }

        private void ensureInputCapacity(
                final int capacity
        ) {
            if (this.input.length < capacity) {
                this.input = new byte[Math.max(capacity, this.input.length * 2)];
            }
        }

        private void ensureOutputCapacity(
                final int capacity
        ) {
            if (this.output.length < capacity) {
                this.output = new byte[Math.max(capacity, this.output.length * 2)];
            }
        }
    }
}