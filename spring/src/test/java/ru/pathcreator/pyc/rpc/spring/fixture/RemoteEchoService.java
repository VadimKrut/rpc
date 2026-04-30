package ru.pathcreator.pyc.rpc.spring.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "spring.remote.echo", channel = "client")
public interface RemoteEchoService {

    @RpcMethod(requestMessageTypeId = 5101, responseMessageTypeId = 5201, timeoutNs = 250_000_000L)
    SpringEchoResponse echo(SpringEchoRequest request);
}
