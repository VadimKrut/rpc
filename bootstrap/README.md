# rpc-bootstrap

`rpc-bootstrap` is the convenience layer that assembles contracts, channels, typed clients, typed servers, encryption and observability into one environment-driven API.

## Features

- `@RpcService` and `@RpcMethod`
- named channel profiles
- auto-routing of services to profiles
- client proxy creation
- bulk service registration
- compile-time metadata for serialization discovery

## Dependency

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>bootstrap</artifactId>
    <version>0.0.1</version>
</dependency>
```