# `admin-ui-wicket`

Standalone operator console over `rpc-admin` built on Apache Wicket and embedded Jetty.

## What this module gives you

- no Spring dependency
- no Node.js toolchain
- no dedicated REST adapter between UI and `rpc-admin`
- embedded web server that can be started from any JVM process
- Russian production-style operator screens for:
  - overview
  - channels
  - services, servers, clients and methods

## Minimal bootstrap

```java
final RpcAdminConsoleServer server = RpcAdminConsoleServer.builder()
        .accessToken("replace-me-with-real-token")
        .registerBootstrapEnvironment("vpn-environment", environment)
        .settings(RpcAdminConsoleSettings.builder()
                .applicationName("VPN Control Plane")
                .host("0.0.0.0")
                .port(8080)
                .contextPath("/")
                .build())
        .build()
        .start();

server.join();
```

## Packaging model

This module is intentionally separate from:

- `admin`
- `spring`
- `spring-boot-starter`

It can be embedded into an existing Java process or wrapped by an application-specific launcher module later, without forcing Spring into the dependency graph.