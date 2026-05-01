package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;
import ru.pathcreator.pyc.rpc.admin.RpcAdminSession;

public final class AdminWebSession extends WebSession {

    private transient RpcAdminSession adminSession;

    public AdminWebSession(final Request request) {
        super(request);
    }

    public boolean signIn(final String accessToken) {
        this.adminSession = RpcAdminConsoleApplication.get().facade().authenticate(accessToken);
        this.bind();
        return true;
    }

    public void signOut() {
        this.invalidate();
        this.adminSession = null;
    }

    public boolean isSignedIn() {
        return this.adminSession != null;
    }

    public RpcAdminSession requireAdminSession() {
        if (this.adminSession == null) {
            throw new IllegalStateException("Admin session is not authenticated");
        }
        return this.adminSession;
    }
}