package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.pathcreator.pyc.rpc.admin.RpcAdmin;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleFacade;
import ru.pathcreator.pyc.rpc.admin.ui.application.TranslationRegistry;
import ru.pathcreator.pyc.rpc.admin.ui.bootstrap.RpcAdminConsoleSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcAdminConsoleApplicationTest {

    private WicketTester tester;

    @AfterEach
    void tearDown() {
        if (this.tester != null) {
            this.tester.destroy();
        }
    }

    @Test
    void shouldRenderLoginPage() {
        this.tester = new WicketTester(this.newApplication());

        this.tester.startPage(LoginPage.class);

        this.tester.assertRenderedPage(LoginPage.class);
    }

    @Test
    void shouldRedirectProtectedPageToLoginWhenSessionIsAnonymous() {
        this.tester = new WicketTester(this.newApplication());

        this.tester.startPage(OverviewPage.class);

        this.tester.assertRenderedPage(LoginPage.class);
    }

    @Test
    void shouldAllowLanguageSwitchInsideSession() {
        this.tester = new WicketTester(this.newApplication());

        final AdminWebSession session = (AdminWebSession) this.tester.getSession();
        assertEquals("ru", session.languageCode());

        session.changeLanguage("en");

        assertEquals("en", session.languageCode());
    }

    private RpcAdminConsoleApplication newApplication() {
        final RpcAdmin admin = RpcAdmin.builder()
                .accessToken("secret-token")
                .build();
        return new RpcAdminConsoleApplication().testing(
                new AdminConsoleFacade(admin),
                RpcAdminConsoleSettings.builder()
                        .applicationName("Test Console")
                        .build(),
                new TranslationRegistry()
        );
    }
}