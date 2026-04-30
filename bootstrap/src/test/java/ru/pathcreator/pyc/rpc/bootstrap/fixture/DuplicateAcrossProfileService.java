package ru.pathcreator.pyc.rpc.bootstrap.fixture;

import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcMethod;
import ru.pathcreator.pyc.rpc.bootstrap.annotation.RpcService;

@RpcService(value = "bootstrap.duplicateAcrossProfile", channel = "plain")
public interface DuplicateAcrossProfileService {

    @RpcMethod(requestMessageTypeId = 1101, responseMessageTypeId = 1201)
    BootstrapEchoResponse duplicate(BootstrapEchoRequest request);
}
