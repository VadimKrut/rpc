package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "bootstrap.plain", channel = "plain")
public interface PlainEchoService {

    @RpcMethod(
            requestMessageTypeId = 1101,
            responseMessageTypeId = 1201,
            timeoutNs = 321_000_000L
    )
    BootstrapEchoResponse echo(BootstrapEchoRequest request);
}
