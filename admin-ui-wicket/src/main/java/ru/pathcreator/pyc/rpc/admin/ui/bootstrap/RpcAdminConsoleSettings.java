package ru.pathcreator.pyc.rpc.admin.ui.bootstrap;

import java.io.Serializable;
import java.util.Objects;

public record RpcAdminConsoleSettings(
        String applicationName,
        String host,
        int port,
        String contextPath,
        boolean developmentMode
) implements Serializable {

    public RpcAdminConsoleSettings {
        applicationName = Objects.requireNonNull(applicationName, "applicationName").trim();
        host = Objects.requireNonNull(host, "host").trim();
        contextPath = normalizeContextPath(contextPath);
        if (applicationName.isEmpty()) {
            throw new IllegalArgumentException("applicationName must not be blank");
        }
        if (host.isEmpty()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String normalizeContextPath(final String value) {
        final String raw = Objects.requireNonNull(value, "contextPath").trim();
        if (raw.isEmpty() || raw.equals("/")) {
            return "/";
        }
        final String normalized = raw.startsWith("/") ? raw : "/" + raw;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    public static final class Builder {

        private String applicationName = "RPC Admin Console";
        private String host = "0.0.0.0";
        private int port = 8080;
        private String contextPath = "/";
        private boolean developmentMode;

        private Builder() {
        }

        public Builder applicationName(final String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder host(final String host) {
            this.host = host;
            return this;
        }

        public Builder port(final int port) {
            this.port = port;
            return this;
        }

        public Builder contextPath(final String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public Builder developmentMode(final boolean developmentMode) {
            this.developmentMode = developmentMode;
            return this;
        }

        public RpcAdminConsoleSettings build() {
            return new RpcAdminConsoleSettings(
                    this.applicationName,
                    this.host,
                    this.port,
                    this.contextPath,
                    this.developmentMode
            );
        }
    }
}