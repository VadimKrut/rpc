package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.resource.PackageResourceReference;
import ru.pathcreator.pyc.rpc.admin.RpcAdminSession;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleDashboard;
import ru.pathcreator.pyc.rpc.admin.ui.application.LanguageOption;

public abstract class BasePage extends WebPage {

    private transient AdminConsoleDashboard dashboard;

    protected BasePage() {
        if (!this.adminWebSession().isSignedIn()) {
            throw new RestartResponseAtInterceptPageException(LoginPage.class);
        }

        this.add(new Label("applicationName", this.application().settings().applicationName()));
        this.add(new Label("brandSubtitle", this.t("shell.brandSubtitle")));
        this.add(new Label("pageTitle", this.t(this.pageTitleKey())));
        this.add(new Label("pageDescription", this.t(this.pageDescriptionKey())));
        this.add(new Label("sidebarFootnote", this.t("shell.sidebarFootnote")));
        this.add(new Label("languageSwitcherLabel", this.t("language.switcher")));
        this.add(new FeedbackPanel("feedback"));
        this.add(this.navigationLink("overviewLink", "overviewLinkLabel", "nav.overview", OverviewPage.class));
        this.add(this.navigationLink("channelsLink", "channelsLinkLabel", "nav.channels", ChannelsPage.class));
        this.add(this.navigationLink("methodsLink", "methodsLinkLabel", "nav.methods", MethodsPage.class));
        final Link<Void> refreshLink = new Link<>("refresh") {
            @Override
            public void onClick() {
                BasePage.this.setResponsePage(BasePage.this.getClass());
            }
        };
        refreshLink.add(new Label("refreshLabel", this.t("action.refresh")));
        this.add(refreshLink);
        final Link<Void> logoutLink = new Link<>("logout") {
            @Override
            public void onClick() {
                BasePage.this.adminWebSession().signOut();
                BasePage.this.setResponsePage(LoginPage.class);
            }
        };
        logoutLink.add(new Label("logoutLabel", this.t("action.logout")));
        this.add(logoutLink);
        this.add(new ListView<>("languageItems", this.application().translations().languages()) {
            @Override
            protected void populateItem(final ListItem<LanguageOption> item) {
                final LanguageOption language = item.getModelObject();
                final Link<Void> link = new Link<>("languageLink") {
                    @Override
                    public void onClick() {
                        BasePage.this.adminWebSession().changeLanguage(language.code());
                        BasePage.this.setResponsePage(BasePage.this.getClass());
                    }
                };
                if (language.code().equals(BasePage.this.adminWebSession().languageCode())) {
                    link.add(AttributeModifier.append("class", "language-link-active"));
                }
                link.add(new Label("languageLabel", language.label()));
                item.add(link);
            }
        });
    }

    protected abstract String pageTitleKey();

    protected abstract String pageDescriptionKey();

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

    protected final String t(final String key) {
        return this.application().translations().text(this.adminWebSession().languageCode(), key);
    }

    @Override
    public void renderHead(final IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(
                new PackageResourceReference(RpcAdminConsoleApplication.class, "admin-console.css")
        ));
    }

    private BookmarkablePageLink<Void> navigationLink(final String id,
                                                      final String labelId,
                                                      final String labelKey,
                                                      final Class<? extends WebPage> pageClass) {
        final BookmarkablePageLink<Void> link = new BookmarkablePageLink<>(id, pageClass);
        if (pageClass.equals(this.getClass())) {
            link.add(AttributeModifier.append("class", "nav-link-active"));
        }
        link.add(new Label(labelId, this.t(labelKey)));
        return link;
    }
}