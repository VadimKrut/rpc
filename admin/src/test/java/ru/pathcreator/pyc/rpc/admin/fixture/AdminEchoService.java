package ru.pathcreator.pyc.rpc.admin.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "admin.echo", channel = "secure")
public interface AdminEchoService {

    @RpcMethod(
            requestMessageTypeId = 1101,
            responseMessageTypeId = 1201,
            timeoutNs = 200_000_000L
    )
    AdminEchoResponse echo(AdminEchoRequest request);
}