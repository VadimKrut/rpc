package ru.pathcreator.pyc.rpc.admin.fixture;

import java.util.Locale;

public final class AdminEchoServiceImpl implements AdminEchoService {

    @Override
    public AdminEchoResponse echo(
            final AdminEchoRequest request
    ) {
        return new AdminEchoResponse(
                request.requestId(),
                request.message().toUpperCase(Locale.ROOT),
                request.amount() + 1
        );
    }
}