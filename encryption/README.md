# rpc-encryption

`rpc-encryption` adds optional payload encryption without changing RPC headers.

## Supported algorithms

- `AES-GCM`
- `ChaCha20-Poly1305`

## Use it when

- you want payload confidentiality between RPC peers
- you want one encryption policy per client/server or channel profile
- you want encryption to stay outside DTO and contract code

## Dependency

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>encryption</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```