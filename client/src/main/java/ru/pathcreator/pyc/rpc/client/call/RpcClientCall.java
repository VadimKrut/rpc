package ru.pathcreator.pyc.rpc.client.call;

import ru.pathcreator.pyc.rpc.client.method.RpcClientMethod;
import ru.pathcreator.pyc.rpc.client.response.RpcClientResult;

public final class RpcClientCall<Q, R> {

    private final long defaultTimeoutNs;
    private final Dispatcher<Q, R> dispatcher;
    private final RpcClientMethod<Q, R> method;

    public RpcClientCall(
            final RpcClientMethod<Q, R> method,
            final long defaultTimeoutNs,
            final Dispatcher<Q, R> dispatcher
    ) {
        this.method = method;
        this.defaultTimeoutNs = defaultTimeoutNs;
        this.dispatcher = dispatcher;
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
        return this.dispatcher.exchange(request, this.defaultTimeoutNs);
    }

    public RpcClientResult<R> exchange(
            final Q request,
            final long timeoutNs
    ) {
        return this.dispatcher.exchange(request, timeoutNs);
    }

    @FunctionalInterface
    public interface Dispatcher<Q, R> {
        RpcClientResult<R> exchange(Q request, long timeoutNs);
    }
}