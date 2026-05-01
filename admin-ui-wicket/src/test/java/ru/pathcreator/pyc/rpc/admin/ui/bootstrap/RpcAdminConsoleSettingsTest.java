package ru.pathcreator.pyc.rpc.admin.ui.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RpcAdminConsoleSettingsTest {

    @Test
    void shouldNormalizeContextPath() {
        final RpcAdminConsoleSettings settings = RpcAdminConsoleSettings.builder()
                .contextPath("admin")
                .build();

        assertEquals("/admin", settings.contextPath());
    }

    @Test
    void shouldKeepRootContextPath() {
        final RpcAdminConsoleSettings settings = RpcAdminConsoleSettings.builder()
                .contextPath("/")
                .build();

        assertEquals("/", settings.contextPath());
    }

    @Test
    void shouldRejectInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> RpcAdminConsoleSettings.builder()
                .port(70000)
                .build());
    }

    @Test
    void shouldRejectBlankApplicationName() {
        assertThrows(IllegalArgumentException.class, () -> new RpcAdminConsoleSettings(
                "   ",
                "127.0.0.1",
                8080,
                "/",
                false
        ));
    }
}