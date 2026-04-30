package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService("bootstrap.secondary")
public interface SecondaryService {

    @RpcMethod(requestMessageTypeId = 111, responseMessageTypeId = 211)
    BootstrapEchoResponse mirror(BootstrapEchoRequest request);
}
