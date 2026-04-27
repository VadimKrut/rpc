package ru.pathcreator.pyc.rpc.core.codex;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public final class RpcMethodRegistry {

    private final Int2ObjectHashMap<RpcResponseDecoder<?>> responseDecoders = new Int2ObjectHashMap<>();

    public <T> void registerResponseDecoder(
            final int responseMessageTypeId,
            final RpcResponseDecoder<T> decoder
    ) {
        responseDecoders.put(responseMessageTypeId, decoder);
    }

    @SuppressWarnings("unchecked")
    public <T> T decode(
            final int length,
            final DirectBuffer buffer,
            final int responseMessageTypeId
    ) {
        final RpcResponseDecoder<?> decoder = responseDecoders.get(responseMessageTypeId);
        if (decoder == null) {
            throw new IllegalStateException("No decoder registered for responseMessageTypeId=" + responseMessageTypeId);
        }
        return (T) decoder.decode(RpcEnvelope.HEADER_LENGTH, length, buffer);
    }
}