package ru.pathcreator.pyc.rpc.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.pathcreator.pyc.rpc.bootstrap.RpcBootstrap;
import ru.pathcreator.pyc.rpc.core.RpcChannel;
import ru.pathcreator.pyc.rpc.core.RpcRuntime;
import ru.pathcreator.pyc.rpc.core.config.RpcChannelConfig;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryption;
import ru.pathcreator.pyc.rpc.encryption.RpcPayloadEncryptions;
import ru.pathcreator.pyc.rpc.spring.fixture.*;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class RpcSpringAutoConfigurationTest {

    private static final AtomicInteger PORTS = new AtomicInteger(37_000);
    private static final AtomicInteger STREAMS = new AtomicInteger(8_100);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RpcSpringAutoConfiguration.class));

    @Test
    void shouldAutoExportRpcEndpointBean() {
        final int basePort = PORTS.getAndAdd(2);
        final int streamId = STREAMS.getAndIncrement();
        this.contextRunner
                .withUserConfiguration(SpringServerTestApplication.class)
                .withPropertyValues(
                        "rpc.spring.scan-packages=ru.pathcreator.pyc.rpc.spring.fixture",
                        "rpc.spring.channels.server.publication-channel=aeron:udp?endpoint=localhost:" + (basePort + 1),
                        "rpc.spring.channels.server.subscription-channel=aeron:udp?endpoint=localhost:" + basePort,
                        "rpc.spring.channels.server.stream-id=" + streamId
                )
                .run(context -> {
                    final RpcRuntime runtime = context.getBean(RpcRuntime.class);
                    final RpcChannel clientChannel = runtime.createChannel(
                            RpcChannelConfig.createDefault(
                                    "aeron:udp?endpoint=localhost:" + basePort,
                                    "aeron:udp?endpoint=localhost:" + (basePort + 1),
                                    streamId
                            )
                    );
                    final ServerEchoService client = RpcBootstrap.client(clientChannel).buildService(ServerEchoService.class);
                    awaitConnectionSetup();
                    final SpringEchoResponse response = client.echo(new SpringEchoRequest(UUID.randomUUID(), "hello"));
                    assertThat(response.message()).isEqualTo("SERVER:hello");
                });
    }

    @Test
    void shouldAutoRegisterRpcClientBeanWithEncryption() {
        final int basePort = PORTS.getAndAdd(2);
        final int streamId = STREAMS.getAndIncrement();
        final byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 11);
        final String keyBase64 = Base64.getEncoder().encodeToString(key);
        final RpcPayloadEncryption serverEncryption = RpcPayloadEncryptions.aesGcm()
                .key(key, false)
                .noncePrefix(0x01020305)
                .initialCounter(1L)
                .build();
        this.contextRunner
                .withUserConfiguration(SpringClientTestApplication.class)
                .withPropertyValues(
                        "rpc.spring.scan-packages=ru.pathcreator.pyc.rpc.spring.fixture",
                        "rpc.spring.channels.client.publication-channel=aeron:udp?endpoint=localhost:" + basePort,
                        "rpc.spring.channels.client.subscription-channel=aeron:udp?endpoint=localhost:" + (basePort + 1),
                        "rpc.spring.channels.client.stream-id=" + streamId,
                        "rpc.spring.channels.client.client-encryption.algorithm=AES_GCM",
                        "rpc.spring.channels.client.client-encryption.key-base64=" + keyBase64,
                        "rpc.spring.channels.client.client-encryption.nonce-prefix=16909060",
                        "rpc.spring.channels.client.client-encryption.initial-counter=1"
                )
                .run(context -> {
                    final RpcRuntime runtime = context.getBean(RpcRuntime.class);
                    final RpcChannel serverChannel = runtime.createChannel(
                            RpcChannelConfig.createDefault(
                                    "aeron:udp?endpoint=localhost:" + (basePort + 1),
                                    "aeron:udp?endpoint=localhost:" + basePort,
                                    streamId
                            )
                    );
                    RpcBootstrap.server(serverChannel)
                            .payloadEncryption(serverEncryption)
                            .service((RemoteEchoService) request -> new SpringEchoResponse(
                                    request.requestId(),
                                    "REMOTE:" + request.message()
                            ))
                            .build();
                    awaitConnectionSetup();
                    final RemoteEchoService client = context.getBean(RemoteEchoService.class);
                    final SpringEchoResponse response = client.echo(new SpringEchoRequest(UUID.randomUUID(), "world"));
                    assertThat(response.message()).isEqualTo("REMOTE:world");
                });
    }

    private static void awaitConnectionSetup() {
        try {
            Thread.sleep(200L);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("connection setup interrupted", error);
        }
    }
}