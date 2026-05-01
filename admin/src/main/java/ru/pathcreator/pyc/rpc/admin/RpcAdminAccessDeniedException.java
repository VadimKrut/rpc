package ru.pathcreator.pyc.rpc.admin;

public final class RpcAdminAccessDeniedException extends RuntimeException {

    public RpcAdminAccessDeniedException() {
        super("invalid admin access token");
    }
}