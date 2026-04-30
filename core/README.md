# rpc-core

`rpc-core` is the low-level Aeron transport layer: framing, correlation ids, waiters, publish/response flow and timeout handling.

## Use it when

- you want maximum control over transport behavior
- you do not need the higher-level typed client/server abstractions
- you are building custom integration on top of the wire protocol

## Dependency

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>core</artifactId>
    <version>0.0.1</version>
</dependency>
```