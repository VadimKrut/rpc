# rpc-serialization

`rpc-serialization` is a Maven plugin that discovers DTO metadata and generates SBE-based serialization adapters.

## Use it when

- you want DTO-first serialization generation
- you want bootstrap metadata to be auto-discovered
- you want generated adapters packaged as part of the build

## Plugin example

```xml
<plugin>
    <groupId>ru.pathcreator.pyc</groupId>
    <artifactId>serialization</artifactId>
    <version>0.0.2-SNAPSHOT</version>
</plugin>
```