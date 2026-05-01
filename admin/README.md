# rpc-admin

`rpc-admin` is an optional control-plane module for runtime, channel, client, server and service telemetry and toggles.

## Features

- access-token protected administrative session
- multi-runtime and multi-profile discovery
- per-channel snapshots and control
- client and server enable/disable
- service and method enable/disable
- wide snapshot DTOs suitable for external UI or HTTP adapters
- multiple runtime/driver discovery
- lazy listener attachment so the data-plane stays quiet until admin access is used

## Dependency

```xml

<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>admin</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```