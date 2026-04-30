package ru.pathcreator.pyc.rpc.spring.fixture;

import ru.pathcreator.pyc.rpc.spring.annotation.RpcEndpoint;

@RpcEndpoint
public final class ServerEchoEndpoint implements ServerEchoService {

    @Override
    public SpringEchoResponse echo(
            final SpringEchoRequest request
    ) {
        return new SpringEchoResponse(request.requestId(), "SERVER:" + request.message());
    }
}
