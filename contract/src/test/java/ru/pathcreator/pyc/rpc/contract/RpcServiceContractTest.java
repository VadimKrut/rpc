package ru.pathcreator.pyc.rpc.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RpcServiceContractTest {

    @Test
    void shouldResolveTypedMethodsByNameAndRequestMessageTypeId() {
        final RpcMethodContract<String, Integer> first = RpcMethodContract.of(
                "service.first",
                String.class,
                Integer.class,
                101,
                201
        );
        final RpcMethodContract<Long, Boolean> second = RpcMethodContract.of(
                "service.second",
                Long.class,
                Boolean.class,
                102,
                202
        );
        final RpcServiceContract contract = RpcServiceContract.builder("service")
                .method(first)
                .method(second)
                .build();

        assertEquals("service", contract.name());
        assertSame(first, contract.requireMethod("service.first", String.class, Integer.class));
        assertSame(second, contract.requireMethod(102, Long.class, Boolean.class));
    }

    @Test
    void shouldRejectDuplicateNamesAndMessageTypeIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RpcServiceContract.builder("dup-name")
                        .method("service.echo", String.class, Integer.class, 101, 201)
                        .method("service.echo", Long.class, Boolean.class, 102, 202)
                        .build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RpcServiceContract.builder("dup-request")
                        .method("service.first", String.class, Integer.class, 101, 201)
                        .method("service.second", Long.class, Boolean.class, 101, 202)
                        .build()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RpcServiceContract.builder("dup-response")
                        .method("service.first", String.class, Integer.class, 101, 201)
                        .method("service.second", Long.class, Boolean.class, 102, 201)
                        .build()
        );
    }

    @Test
    void shouldRejectTypeMismatchDuringTypedLookup() {
        final RpcServiceContract contract = RpcServiceContract.of(
                "typed",
                RpcMethodContract.of("service.echo", String.class, Integer.class, 101, 201)
        );

        final IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> contract.requireMethod("service.echo", String.class, Long.class)
        );
        assertEquals(
                "method 'service.echo' response type mismatch: expected java.lang.Integer, got java.lang.Long",
                error.getMessage()
        );
    }
}