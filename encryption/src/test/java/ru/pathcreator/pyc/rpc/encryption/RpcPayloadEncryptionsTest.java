package ru.pathcreator.pyc.rpc.encryption;

import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RpcPayloadEncryptionsTest {

    @Test
    void shouldRoundTripWithAesGcm() {
        assertRoundTrip(
                RpcPayloadEncryptions.aesGcm()
                        .key(repeat((byte) 0x11, 16))
                        .noncePrefix(0x10203040)
                        .initialCounter(7L)
                        .build()
        );
    }

    @Test
    void shouldRoundTripWithChaCha20Poly1305() {
        assertRoundTrip(
                RpcPayloadEncryptions.chaCha20Poly1305()
                        .key(repeat((byte) 0x22, 32))
                        .noncePrefix(0x55667788)
                        .initialCounter(19L)
                        .build()
        );
    }

    @Test
    void shouldFailDecryptionWhenKeyDiffers() {
        final RpcPayloadEncryption encryptor = RpcPayloadEncryptions.aesGcm()
                .key(repeat((byte) 0x33, 16))
                .noncePrefix(0x11223344)
                .initialCounter(1L)
                .build();
        final RpcPayloadEncryption decryptor = RpcPayloadEncryptions.aesGcm()
                .key(repeat((byte) 0x44, 16))
                .noncePrefix(0x11223344)
                .initialCounter(1L)
                .build();
        final ExpandableArrayBuffer plain = new ExpandableArrayBuffer(64);
        final ExpandableArrayBuffer encrypted = new ExpandableArrayBuffer(128);
        plain.putStringWithoutLengthUtf8(0, "hello");
        final int encryptedLength = encryptor.encrypt(plain, 0, 5, encrypted, 0);

        assertThrows(
                RpcPayloadEncryptionException.class,
                () -> decryptor.decrypt(encrypted, 0, encryptedLength, new ExpandableArrayBuffer(64), 0)
        );
    }

    @Test
    void shouldWipeSourceKeyWhenRequested() {
        final byte[] key = repeat((byte) 0x55, 16);

        RpcPayloadEncryptions.aesGcm()
                .key(key, true)
                .noncePrefix(0x01020304)
                .initialCounter(1L)
                .build();

        assertArrayEquals(new byte[16], key);
    }

    private static void assertRoundTrip(
            final RpcPayloadEncryption encryption
    ) {
        final byte[] expected = "payload-123".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        final ExpandableArrayBuffer plain = new ExpandableArrayBuffer(64);
        final ExpandableArrayBuffer encrypted = new ExpandableArrayBuffer(128);
        final ExpandableArrayBuffer decrypted = new ExpandableArrayBuffer(64);
        plain.putBytes(0, expected);

        final int encryptedLength = encryption.encrypt(plain, 0, expected.length, encrypted, 0);
        assertNotEquals(expected.length, encryptedLength);
        final int decryptedLength = encryption.decrypt(encrypted, 0, encryptedLength, decrypted, 0);

        final byte[] actual = new byte[decryptedLength];
        decrypted.getBytes(0, actual);
        assertArrayEquals(expected, actual);
    }

    private static byte[] repeat(
            final byte value,
            final int size
    ) {
        final byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }
}