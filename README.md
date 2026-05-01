# rpc

`rpc` is a modular low-latency RPC stack built around Aeron transport, explicit contracts, optional payload encryption, observability, bootstrap ergonomics and Spring Boot integration.

## Modules

- `codec`: codec SPI and registry.
- `contract`: method and service contracts.
- `core`: low-level Aeron transport and framing.
- `encryption`: optional AES-GCM and ChaCha20-Poly1305 payload encryption.
- `client`: typed RPC client.
- `server`: typed RPC server dispatcher.
- `observability`: low-overhead metrics.
- `bootstrap`: high-level environment, annotated services and contract-aware wiring.
- `admin`: optional in-process control-plane for runtimes, channels, clients, servers and services.
- `spring`: Spring Boot integration.
- `spring-boot-starter`: one-dependency Spring Boot starter.
- `serialization`: Maven plugin for DTO discovery and SBE adapter generation.

See module-specific guides:

- [codec](./codec/README.md)
- [contract](./contract/README.md)
- [core](./core/README.md)
- [encryption](./encryption/README.md)
- [client](./client/README.md)
- [server](./server/README.md)
- [observability](./observability/README.md)
- [bootstrap](./bootstrap/README.md)
- [admin](./admin/README.md)
- [spring](./spring/README.md)
- [spring-boot-starter](./spring-boot-starter/README.md)
- [serialization](./serialization/README.md)

## Maven coordinates

Repository:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/VadimKrut/rpc</url>
    </repository>
</repositories>
```

Pick only the module you need:

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>core</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

Or use a higher-level module:

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>bootstrap</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

Optional admin-plane:

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>admin</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

For Spring Boot:

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>spring-boot-starter</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

## Quick start

Define a contract:

```java
@RpcService(value = "orders", channel = "secure")
public interface OrderService {

    @RpcMethod(
            requestMessageTypeId = 101,
            responseMessageTypeId = 201,
            timeoutNs = 200_000_000L
    )
    OrderResponse create(OrderRequest request);
}
```

Wire it with bootstrap:

```java
final RpcBootstrapEnvironment serverEnv = RpcBootstrap.environment()
        .channel("secure", serverChannel)
        .done()
        .build();

final RpcBootstrapEnvironment clientEnv = RpcBootstrap.environment()
        .channel("secure", clientChannel)
        .done()
        .build();

serverEnv.service(new OrderServiceImpl());

final OrderService client = clientEnv.client(OrderService.class);
final OrderResponse response = client.create(new OrderRequest(42L, "AAPL", 100));
```

## Build

```bash
mvn verify
```

## Publish

On GitHub Actions, pushes to `master` publish to GitHub Packages.

- If `revision` ends with `-SNAPSHOT`, the snapshot is redeployed and no git tag is created.
- If `revision` is a release version like `0.0.2`, the build is deployed and a git tag with the exact same name is pushed automatically.

Local publication works with:

```bash
mvn -DskipTests deploy
```