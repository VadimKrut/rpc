package ru.pathcreator.pyc.rpc.spring.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "spring.server.echo", channel = "server")
public interface ServerEchoService {

    @RpcMethod(requestMessageTypeId = 4101, responseMessageTypeId = 4201, timeoutNs = 150_000_000L)
    SpringEchoResponse echo(SpringEchoRequest request);
}
