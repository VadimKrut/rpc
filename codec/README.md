# rpc-codec

`rpc-codec` contains the codec SPI used by the rest of the stack.

## Use it when

- you want to implement your own `SerializationCodec`
- you want to register codec factories
- you want the smallest dependency surface

## Dependency

```xml
<dependency>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>codec</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</dependency>
```

See the [root README](../README.md) for the full module map.