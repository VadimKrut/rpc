package ru.pathcreator.pyc.rpc.spring;

import org.springframework.beans.factory.BeanCreationException;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrap;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapChannelProfileBuilder;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironment;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrapEnvironmentBuilder;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryptions;
import ru.pathcreator.pyc.rpc.spring.config.RpcSpringProperties;

import java.util.Base64;

final class RpcSpringSupport {

    private RpcSpringSupport() {
    }

    static RpcBootstrapEnvironment createEnvironment(
            final RpcRuntime runtime,
            final RpcSpringProperties properties
    ) {
        final RpcBootstrapEnvironmentBuilder environment = RpcBootstrap.environment();
        for (final var entry : properties.getChannels().entrySet()) {
            final String name = entry.getKey();
            final RpcSpringProperties.Channel channel = entry.getValue();
            final RpcBootstrapChannelProfileBuilder profile = environment.channel(
                    name,
                    runtime.createChannel(toChannelConfig(channel))
            );
            profile.clientDefaultTimeoutNs(channel.getClientDefaultTimeoutNs());
            profile.clientPayloadEncryption(toEncryption(channel.getClientEncryption(), "client", name));
            profile.serverPayloadEncryption(toEncryption(channel.getServerEncryption(), "server", name));
            profile.done();
        }
        return environment.build();
    }

    static RpcChannelConfig toChannelConfig(
            final RpcSpringProperties.Channel channel
    ) {
        return new RpcChannelConfig(
                required(channel.getPublicationChannel(), "publicationChannel"),
                required(channel.getSubscriptionChannel(), "subscriptionChannel"),
                channel.getStreamId(),
                channel.getFragmentLimit(),
                channel.getListenerMaxYields(),
                channel.getPublisherMaxYields(),
                channel.getWaitersInitialCapacity(),
                channel.getWaitersLoadFactor()
        );
    }

    static RpcPayloadEncryption toEncryption(
            final RpcSpringProperties.Encryption encryption,
            final String side,
            final String channelName
    ) {
        final RpcSpringProperties.Algorithm algorithm = encryption.getAlgorithm();
        if (algorithm == null || algorithm == RpcSpringProperties.Algorithm.NONE) {
            return RpcPayloadEncryption.NOOP;
        }
        final String keyBase64 = encryption.getKeyBase64();
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new BeanCreationException(
                    "Missing keyBase64 for " + side + " encryption on rpc.spring.channels." + channelName
            );
        }
        final byte[] key;
        try {
            key = Base64.getDecoder().decode(keyBase64);
        } catch (final IllegalArgumentException error) {
            throw new BeanCreationException(
                    "Invalid base64 key for " + side + " encryption on rpc.spring.channels." + channelName,
                    error
            );
        }
        return switch (algorithm) {
            case AES_GCM -> RpcPayloadEncryptions.aesGcm()
                    .key(key, true)
                    .noncePrefix(encryption.getNoncePrefix())
                    .initialCounter(encryption.getInitialCounter())
                    .build();
            case CHACHA20_POLY1305 -> RpcPayloadEncryptions.chaCha20Poly1305()
                    .key(key, true)
                    .noncePrefix(encryption.getNoncePrefix())
                    .initialCounter(encryption.getInitialCounter())
                    .build();
            case NONE -> RpcPayloadEncryption.NOOP;
        };
    }

    private static String required(
            final String value,
            final String field
    ) {
        if (value == null || value.isBlank()) {
            throw new BeanCreationException("rpc.spring channel field '" + field + "' must not be blank");
        }
        return value;
    }
}