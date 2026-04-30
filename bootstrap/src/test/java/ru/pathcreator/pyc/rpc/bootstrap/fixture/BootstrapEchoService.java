package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "bootstrap.echo", channel = "secure")
public interface BootstrapEchoService {

    @RpcMethod(
            requestMessageTypeId = 1101,
            responseMessageTypeId = 1201,
            timeoutNs = 123_000_000L
    )
    BootstrapEchoResponse echo(BootstrapEchoRequest request);
}
