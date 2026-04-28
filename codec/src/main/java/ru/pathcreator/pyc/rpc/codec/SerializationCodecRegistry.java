package ru.pathcreator.pyc.rpc.codec;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

public final class SerializationCodecRegistry {

    private final Map<Class<?>, SerializationCodec<?>> codecs;

    private SerializationCodecRegistry(final Map<Class<?>, SerializationCodec<?>> codecs) {
        this.codecs = codecs;
    }

    public static SerializationCodecRegistry load() {
        final ServiceLoader<SerializationCodecFactory> loader = ServiceLoader.load(SerializationCodecFactory.class);
        final Map<Class<?>, SerializationCodec<?>> codecs = new LinkedHashMap<>();
        for (final SerializationCodecFactory factory : loader) {
            final Collection<SerializationCodec<?>> values = factory.codecs();
            for (final SerializationCodec<?> codec : values) {
                codecs.put(codec.javaType(), codec);
            }
        }
        return new SerializationCodecRegistry(codecs);
    }

    @SuppressWarnings("unchecked")
    public <T> SerializationCodec<T> codecFor(final Class<T> type) {
        final SerializationCodec<?> codec = codecs.get(type);
        if (codec == null) {
            throw new CodecNotFoundException(type);
        }
        return (SerializationCodec<T>) codec;
    }
}