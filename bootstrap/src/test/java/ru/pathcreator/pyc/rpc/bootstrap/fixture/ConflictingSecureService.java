package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "bootstrap.conflict", channel = "secure")
public interface ConflictingSecureService {

    @RpcMethod(requestMessageTypeId = 1101, responseMessageTypeId = 2201)
    BootstrapEchoResponse conflict(BootstrapEchoRequest request);
}
