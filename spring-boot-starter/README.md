# rpc-spring-boot-starter

`rpc-spring-boot-starter` is the shortest path to use the RPC stack inside a Spring Boot application.

## Dependency

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

After that, define `@RpcService` contracts, add `@RpcEndpoint` implementations and configure `rpc.spring.channels.*` in `application.yml`.