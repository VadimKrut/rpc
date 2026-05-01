package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.PackageResourceReference;
import ru.pathcreator.pyc.rpc.admin.ui.application.LanguageOption;

public final class LoginPage extends WebPage {

    public LoginPage() {
        if (((AdminWebSession) this.getSession()).isSignedIn()) {
            this.setResponsePage(OverviewPage.class);
            return;
        }

        final Model<String> tokenModel = Model.of("");
        final Form<Void> form = new Form<>("loginForm") {
            @Override
            protected void onSubmit() {
                try {
                    ((AdminWebSession) LoginPage.this.getSession()).signIn(tokenModel.getObject());
                    LoginPage.this.setResponsePage(OverviewPage.class);
                } catch (final RuntimeException exception) {
                    error(LoginPage.this.t("login.invalidToken"));
                }
            }
        };
        form.add(new Label("tokenLabel", this.t("login.accessToken")));
        form.add(new PasswordTextField("accessToken", tokenModel).setRequired(true));
        form.add(new Label("submitLabel", this.t("login.submit")));

        this.add(new Label("applicationName", RpcAdminConsoleApplication.get().settings().applicationName()));
        this.add(new Label("caption", this.t("login.caption")));
        this.add(new Label("languageSwitcherLabel", this.t("language.switcher")));
        this.add(new FeedbackPanel("feedback"));
        this.add(form);
        this.add(new ListView<>("languageItems", RpcAdminConsoleApplication.get().translations().languages()) {
            @Override
            protected void populateItem(final ListItem<LanguageOption> item) {
                final LanguageOption language = item.getModelObject();
                final Link<Void> link = new Link<>("languageLink") {
                    @Override
                    public void onClick() {
                        ((AdminWebSession) LoginPage.this.getSession()).changeLanguage(language.code());
                        LoginPage.this.setResponsePage(LoginPage.class);
                    }
                };
                if (language.code().equals(((AdminWebSession) LoginPage.this.getSession()).languageCode())) {
                    link.add(AttributeModifier.append("class", "language-link-active"));
                }
                link.add(new Label("languageLabel", language.label()));
                item.add(link);
            }
        });
    }

    @Override
    public void renderHead(final IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(
                new PackageResourceReference(RpcAdminConsoleApplication.class, "admin-console.css")
        ));
    }

    private String t(final String key) {
        return RpcAdminConsoleApplication.get().translations()
                .text(((AdminWebSession) this.getSession()).languageCode(), key);
    }
}