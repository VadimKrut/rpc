package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService("bootstrap.invalid")
public interface InvalidBootstrapService {

    @RpcMethod(requestMessageTypeId = 1301, responseMessageTypeId = 1401)
    void invalid(BootstrapEchoRequest request);
}
