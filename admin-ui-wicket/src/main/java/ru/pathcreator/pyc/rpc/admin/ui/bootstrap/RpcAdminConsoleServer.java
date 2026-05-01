package ru.pathcreator.pyc.rpc.admin.ui.bootstrap;

import jakarta.servlet.DispatcherType;
import org.apache.wicket.protocol.http.ContextParamWebApplicationFactory;
import org.apache.wicket.protocol.http.WicketFilter;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleFacade;
import ru.pathcreator.pyc.rpc.admin.ui.web.RpcAdminConsoleApplication;

import java.net.URI;
import java.util.EnumSet;
import java.util.Objects;

public final class RpcAdminConsoleServer implements AutoCloseable {

    public static final String SETTINGS_ATTRIBUTE = RpcAdminConsoleServer.class.getName() + ".settings";
    public static final String FACADE_ATTRIBUTE = RpcAdminConsoleServer.class.getName() + ".facade";

    private final AdminConsoleFacade facade;
    private final RpcAdminConsoleSettings settings;
    private final Server server;

    RpcAdminConsoleServer(final AdminConsoleFacade facade,
                          final RpcAdminConsoleSettings settings) {
        this.facade = Objects.requireNonNull(facade, "facade");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.server = this.createServer();
    }

    public static RpcAdminConsoleServerBuilder builder() {
        return new RpcAdminConsoleServerBuilder();
    }

    public RpcAdminConsoleSettings settings() {
        return this.settings;
    }

    public RpcAdminConsoleServer start() throws Exception {
        this.server.start();
        return this;
    }

    public void join() throws InterruptedException {
        this.server.join();
    }

    public URI baseUri() {
        final String path = this.settings.contextPath().equals("/") ? "/" : this.settings.contextPath();
        return URI.create("http://" + this.settings.host() + ":" + this.port() + path);
    }

    public int port() {
        return ((ServerConnector) this.server.getConnectors()[0]).getLocalPort();
    }

    public void stop() throws Exception {
        this.server.stop();
    }

    @Override
    public void close() throws Exception {
        this.stop();
    }

    private Server createServer() {
        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setHost(this.settings.host());
        connector.setPort(this.settings.port());
        server.addConnector(connector);

        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath(this.settings.contextPath());
        context.setAttribute(SETTINGS_ATTRIBUTE, this.settings);
        context.setAttribute(FACADE_ATTRIBUTE, this.facade);

        final FilterHolder wicketFilter = new FilterHolder(WicketFilter.class);
        wicketFilter.setInitParameter(
                ContextParamWebApplicationFactory.APP_CLASS_PARAM,
                RpcAdminConsoleApplication.class.getName()
        );
        wicketFilter.setInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        context.addFilter(
                wicketFilter,
                "/*",
                EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR)
        );

        context.addServlet(new ServletHolder(new DefaultServlet()), "/");
        server.setHandler(context);
        return server;
    }
}