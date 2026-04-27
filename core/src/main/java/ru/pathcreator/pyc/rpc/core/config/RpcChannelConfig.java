package ru.pathcreator.pyc.rpc.core.config;

public record RpcChannelConfig(
        String publicationChannel,
        String subscriptionChannel,
        int streamId,
        int fragmentLimit,
        int listenerMaxYields,
        int publisherMaxYields,
        int waitersInitialCapacity,
        float waitersLoadFactor
) {
    public RpcChannelConfig {
        if (publicationChannel == null || publicationChannel.isBlank()) {
            throw new IllegalArgumentException("publicationChannel must not be blank");
        }
        if (subscriptionChannel == null || subscriptionChannel.isBlank()) {
            throw new IllegalArgumentException("subscriptionChannel must not be blank");
        }
        if (streamId <= 0) {
            throw new IllegalArgumentException("streamId must be > 0");
        }
        if (fragmentLimit <= 0) {
            throw new IllegalArgumentException("fragmentLimit must be > 0");
        }
        if (listenerMaxYields < 0) {
            throw new IllegalArgumentException("listenerMaxYields must be >= 0");
        }
        if (publisherMaxYields < 0) {
            throw new IllegalArgumentException("publisherMaxYields must be >= 0");
        }
    }

    public static RpcChannelConfig createDefault(
            final String publicationChannel,
            final String subscriptionChannel,
            final int streamId
    ) {
        return new RpcChannelConfig(
                publicationChannel,
                subscriptionChannel,
                streamId,
                32,
                512,
                20_000,
                1_048_576,
                0.75f
        );
    }
}