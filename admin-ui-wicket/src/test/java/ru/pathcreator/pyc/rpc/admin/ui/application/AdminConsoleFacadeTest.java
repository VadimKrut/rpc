package ru.pathcreator.pyc.rpc.admin.ui.application;

import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.admin.RpcAdmin;
import ru.pathcreator.pyc.rpc.admin.RpcAdminAccessDeniedException;
import ru.pathcreator.pyc.rpc.admin.RpcAdminSession;

import static org.junit.jupiter.api.Assertions.*;

class AdminConsoleFacadeTest {

    @Test
    void shouldAuthenticateAndProduceEmptyDashboardForFreshAdmin() {
        final RpcAdmin admin = RpcAdmin.builder()
                .accessToken("secret-token")
                .build();
        final AdminConsoleFacade facade = new AdminConsoleFacade(admin);

        final RpcAdminSession session = facade.authenticate("secret-token");
        final AdminConsoleDashboard dashboard = facade.dashboard(session);

        assertNotNull(session);
        assertNotNull(dashboard);
        assertNotNull(dashboard.snapshot());
        assertNotNull(dashboard.telemetry());
        assertTrue(dashboard.methodRows().isEmpty());
        assertEquals(0, dashboard.snapshot().summary().runtimeCount());
        assertEquals(0, dashboard.snapshot().summary().channelCount());
        assertEquals(0, dashboard.snapshot().summary().serviceCount());
    }

    @Test
    void shouldRejectWrongAccessToken() {
        final RpcAdmin admin = RpcAdmin.builder()
                .accessToken("secret-token")
                .build();
        final AdminConsoleFacade facade = new AdminConsoleFacade(admin);

        assertThrows(RpcAdminAccessDeniedException.class, () -> facade.authenticate("wrong-token"));
    }
}