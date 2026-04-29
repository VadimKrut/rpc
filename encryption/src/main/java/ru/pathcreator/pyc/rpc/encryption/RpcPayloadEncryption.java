package ru.pathcreator.pyc.rpc.encryption;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface RpcPayloadEncryption {

    RpcPayloadEncryption NOOP = new RpcPayloadEncryption() {
        @Override
        public int maxEncryptedLength(final int plainLength) {
            return plainLength;
        }

        @Override
        public int encrypt(
                final DirectBuffer source,
                final int sourceOffset,
                final int sourceLength,
                final MutableDirectBuffer target,
                final int targetOffset
        ) {
            target.putBytes(targetOffset, source, sourceOffset, sourceLength);
            return sourceLength;
        }

        @Override
        public int decrypt(
                final DirectBuffer source,
                final int sourceOffset,
                final int sourceLength,
                final MutableDirectBuffer target,
                final int targetOffset
        ) {
            target.putBytes(targetOffset, source, sourceOffset, sourceLength);
            return sourceLength;
        }
    };

    int maxEncryptedLength(int plainLength);

    int encrypt(
            DirectBuffer source,
            int sourceOffset,
            int sourceLength,
            MutableDirectBuffer target,
            int targetOffset
    );

    int decrypt(
            DirectBuffer source,
            int sourceOffset,
            int sourceLength,
            MutableDirectBuffer target,
            int targetOffset
    );
}