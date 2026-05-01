package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.Application;
import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleFacade;
import ru.pathcreator.pyc.rpc.admin.ui.bootstrap.RpcAdminConsoleServer;
import ru.pathcreator.pyc.rpc.admin.ui.bootstrap.RpcAdminConsoleSettings;

public final class RpcAdminConsoleApplication extends WebApplication {

    private AdminConsoleFacade facade;
    private RpcAdminConsoleSettings settings;

    public static RpcAdminConsoleApplication get() {
        return (RpcAdminConsoleApplication) Application.get();
    }

    @Override
    public Class<? extends WebPage> getHomePage() {
        return OverviewPage.class;
    }

    @Override
    public RuntimeConfigurationType getConfigurationType() {
        return this.settings != null && this.settings.developmentMode()
                ? RuntimeConfigurationType.DEVELOPMENT
                : RuntimeConfigurationType.DEPLOYMENT;
    }

    @Override
    public void init() {
        super.init();
        this.settings = (RpcAdminConsoleSettings) this.getServletContext().getAttribute(RpcAdminConsoleServer.SETTINGS_ATTRIBUTE);
        this.facade = (AdminConsoleFacade) this.getServletContext().getAttribute(RpcAdminConsoleServer.FACADE_ATTRIBUTE);
        if (this.settings == null || this.facade == null) {
            throw new IllegalStateException("RpcAdminConsoleApplication requires bootstrap attributes");
        }
        this.getMarkupSettings().setStripWicketTags(true);
        this.mountPage("/login", LoginPage.class);
        this.mountPage("/overview", OverviewPage.class);
        this.mountPage("/channels", ChannelsPage.class);
        this.mountPage("/methods", MethodsPage.class);
    }

    @Override
    public AdminWebSession newSession(final org.apache.wicket.request.Request request,
                                      final org.apache.wicket.request.Response response) {
        return new AdminWebSession(request);
    }

    public AdminConsoleFacade facade() {
        return this.facade;
    }

    public RpcAdminConsoleSettings settings() {
        return this.settings;
    }
}