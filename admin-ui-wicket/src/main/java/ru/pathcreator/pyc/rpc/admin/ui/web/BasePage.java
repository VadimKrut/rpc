package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.resource.PackageResourceReference;
import ru.pathcreator.pyc.rpc.admin.RpcAdminSession;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleDashboard;

public abstract class BasePage extends WebPage {

    private transient AdminConsoleDashboard dashboard;

    protected BasePage() {
        if (!this.adminWebSession().isSignedIn()) {
            throw new RestartResponseAtInterceptPageException(LoginPage.class);
        }

        this.add(new Label("applicationName", this.application().settings().applicationName()));
        this.add(new Label("pageTitle", this.pageTitle()));
        this.add(new Label("pageDescription", this.pageDescription()));
        this.add(new FeedbackPanel("feedback"));
        this.add(this.navigationLink("overviewLink", OverviewPage.class));
        this.add(this.navigationLink("channelsLink", ChannelsPage.class));
        this.add(this.navigationLink("methodsLink", MethodsPage.class));
        this.add(new Link<Void>("refresh") {
            @Override
            public void onClick() {
                BasePage.this.setResponsePage(BasePage.this.getClass());
            }
        });
        this.add(new Link<Void>("logout") {
            @Override
            public void onClick() {
                BasePage.this.adminWebSession().signOut();
                BasePage.this.setResponsePage(LoginPage.class);
            }
        });
    }

    protected abstract String pageTitle();

    protected abstract String pageDescription();

    protected final RpcAdminConsoleApplication application() {
        return RpcAdminConsoleApplication.get();
    }

    protected final AdminWebSession adminWebSession() {
        return (AdminWebSession) this.getSession();
    }

    protected final RpcAdminSession adminSession() {
        return this.adminWebSession().requireAdminSession();
    }

    protected final AdminConsoleDashboard dashboard() {
        if (this.dashboard == null) {
            this.dashboard = this.application().facade().dashboard(this.adminSession());
        }
        return this.dashboard;
    }

    @Override
    public void renderHead(final IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(
                new PackageResourceReference(RpcAdminConsoleApplication.class, "admin-console.css")
        ));
    }

    private BookmarkablePageLink<Void> navigationLink(final String id,
                                                      final Class<? extends WebPage> pageClass) {
        final BookmarkablePageLink<Void> link = new BookmarkablePageLink<>(id, pageClass);
        if (pageClass.equals(this.getClass())) {
            link.add(AttributeModifier.append("class", "nav-link-active"));
        }
        return link;
    }
}