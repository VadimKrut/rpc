package ru.pathcreator.pyc.rpc.codec;

import java.util.Collection;

public interface SerializationCodecFactory {

    Collection<SerializationCodec<?>> codecs();
}