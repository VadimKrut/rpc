package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;
import ru.pathcreator.pyc.rpc.admin.RpcAdminSession;

import java.util.Locale;

public final class AdminWebSession extends WebSession {

    private transient RpcAdminSession adminSession;
    private String languageCode;

    public AdminWebSession(final Request request) {
        super(request);
        this.languageCode = RpcAdminConsoleApplication.get().translations().defaultLanguageCode();
        this.setLocale(this.resolveLocale());
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

    public String languageCode() {
        return this.languageCode;
    }

    public void changeLanguage(final String languageCode) {
        if (!RpcAdminConsoleApplication.get().translations().supports(languageCode)) {
            return;
        }
        this.languageCode = languageCode;
        this.setLocale(this.resolveLocale());
        this.bind();
    }

    private Locale resolveLocale() {
        return RpcAdminConsoleApplication.get().translations().locale(this.languageCode);
    }
}