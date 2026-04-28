package ru.pathcreator.pyc.rpc.client.call;

import ru.pathcreator.pyc.rpc.client.context.RpcClientContext;
import ru.pathcreator.pyc.rpc.client.method.RpcClientMethod;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;

import java.util.function.BiFunction;

public final class RpcClientCall<Q, R> {

    private final RpcClientMethod<Q, R> method;
    private final long defaultTimeoutNs;
    private final BiFunction<RpcClientContext, Q, RpcClientResult<R>> exchangeFunction;

    public RpcClientCall(
            final RpcClientMethod<Q, R> method,
            final long defaultTimeoutNs,
            final BiFunction<RpcClientContext, Q, RpcClientResult<R>> exchangeFunction
    ) {
        this.method = method;
        this.defaultTimeoutNs = defaultTimeoutNs;
        this.exchangeFunction = exchangeFunction;
    }

    public RpcClientMethod<Q, R> method() {
        return this.method;
    }

    public R send(
            final Q request
    ) {
        return this.exchange(request).requireSuccess();
    }

    public R send(
            final Q request,
            final long timeoutNs
    ) {
        return this.exchange(request, timeoutNs).requireSuccess();
    }

    public RpcClientResult<R> exchange(
            final Q request
    ) {
        return this.exchange(request, this.defaultTimeoutNs);
    }

    public RpcClientResult<R> exchange(
            final Q request,
            final long timeoutNs
    ) {
        return this.exchangeFunction.apply(new RpcClientContext(this.method, timeoutNs), request);
    }
}