package ru.pathcreator.pyc.rpc.core;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class RpcRuntime implements AutoCloseable {

    private final String runtimeId;
    private final Aeron aeron;
    private final MediaDriver mediaDriver;
    private final List<RpcChannel> channels = new ArrayList<>();
    private volatile boolean closed;

    private RpcRuntime(
            final String runtimeId,
            final Aeron aeron,
            final MediaDriver mediaDriver
    ) {
        this.runtimeId = runtimeId;
        this.mediaDriver = mediaDriver;
        this.aeron = aeron;
    }

    public static RpcRuntime launchEmbedded() {
        final MediaDriver.Context mediaDriverContext = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .threadingMode(io.aeron.driver.ThreadingMode.SHARED)
                .aeronDirectoryName("rpc-" + UUID.randomUUID());
        final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverContext);
        final Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(mediaDriver.aeronDirectoryName()));
        return new RpcRuntime(UUID.randomUUID().toString(), aeron, mediaDriver);
    }

    public RpcChannel createChannel(
            final RpcChannelConfig config
    ) {
        final RpcChannel channel = new RpcChannel(aeron, config);
        channels.add(channel);
        return channel;
    }

    public String runtimeId() {
        return this.runtimeId;
    }

    public String aeronDirectoryName() {
        return this.mediaDriver.aeronDirectoryName();
    }

    public List<RpcChannel> channels() {
        return Collections.unmodifiableList(new ArrayList<>(this.channels));
    }

    public boolean isClosed() {
        return this.closed;
    }

    @Override
    public void close() {
        this.closed = true;
        for (int i = channels.size() - 1; i >= 0; i--) {
            channels.get(i).close();
        }
        aeron.close();
        mediaDriver.close();
    }
}