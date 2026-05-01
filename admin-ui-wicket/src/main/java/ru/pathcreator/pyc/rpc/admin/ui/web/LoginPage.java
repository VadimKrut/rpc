package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.PackageResourceReference;

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
                    error("Ключ доступа не принят. Проверьте токен и повторите попытку.");
                }
            }
        };
        form.add(new PasswordTextField("accessToken", tokenModel).setRequired(true));

        this.add(new Label("applicationName", RpcAdminConsoleApplication.get().settings().applicationName()));
        this.add(new Label("caption", "Защищённая операторская консоль для модулей rpc-admin без Spring и без отдельного REST-слоя."));
        this.add(new FeedbackPanel("feedback"));
        this.add(form);
    }

    @Override
    public void renderHead(final IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(
                new PackageResourceReference(RpcAdminConsoleApplication.class, "admin-console.css")
        ));
    }
}