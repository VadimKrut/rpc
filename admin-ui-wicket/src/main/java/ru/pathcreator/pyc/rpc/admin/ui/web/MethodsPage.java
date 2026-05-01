package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.Model;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminClientSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminServerSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminServiceSnapshot;
import ru.pathcreator.pyc.rpc.admin.ui.application.MethodRow;
import ru.pathcreator.pyc.rpc.admin.ui.application.UiFormatters;

public final class MethodsPage extends BasePage {

    public MethodsPage() {
        this.add(new Label("serviceSectionTitle", this.t("methods.services.title")));
        this.add(new Label("serviceSectionDescription", this.t("methods.services.description")));
        this.add(new Label("serviceHeaderName", this.t("methods.services.header.name")));
        this.add(new Label("serviceHeaderLocation", this.t("methods.services.header.location")));
        this.add(new Label("serviceHeaderChannel", this.t("methods.services.header.channel")));
        this.add(new Label("serviceHeaderState", this.t("methods.services.header.state")));
        this.add(new Label("serviceHeaderMethods", this.t("methods.services.header.methods")));
        this.add(new Label("serviceHeaderAction", this.t("methods.services.header.action")));

        this.add(new Label("serverSectionTitle", this.t("methods.servers.title")));
        this.add(new Label("serverSectionDescription", this.t("methods.servers.description")));
        this.add(new Label("serverHeaderName", this.t("methods.servers.header.name")));
        this.add(new Label("serverHeaderLocation", this.t("methods.servers.header.location")));
        this.add(new Label("serverHeaderChannel", this.t("methods.servers.header.channel")));
        this.add(new Label("serverHeaderState", this.t("methods.servers.header.state")));
        this.add(new Label("serverHeaderRequests", this.t("methods.servers.header.requests")));
        this.add(new Label("serverHeaderAction", this.t("methods.servers.header.action")));

        this.add(new Label("clientSectionTitle", this.t("methods.clients.title")));
        this.add(new Label("clientSectionDescription", this.t("methods.clients.description")));
        this.add(new Label("clientHeaderName", this.t("methods.clients.header.name")));
        this.add(new Label("clientHeaderLocation", this.t("methods.clients.header.location")));
        this.add(new Label("clientHeaderChannel", this.t("methods.clients.header.channel")));
        this.add(new Label("clientHeaderState", this.t("methods.clients.header.state")));
        this.add(new Label("clientHeaderCalls", this.t("methods.clients.header.calls")));
        this.add(new Label("clientHeaderAction", this.t("methods.clients.header.action")));

        this.add(new Label("catalogSectionTitle", this.t("methods.catalog.title")));
        this.add(new Label("catalogSectionDescription", this.t("methods.catalog.description")));
        this.add(new Label("catalogHeaderMethod", this.t("methods.catalog.header.method")));
        this.add(new Label("catalogHeaderType", this.t("methods.catalog.header.type")));
        this.add(new Label("catalogHeaderSignature", this.t("methods.catalog.header.signature")));
        this.add(new Label("catalogHeaderState", this.t("methods.catalog.header.state")));
        this.add(new Label("catalogHeaderChannel", this.t("methods.catalog.header.channel")));
        this.add(new Label("catalogHeaderCalls", this.t("methods.catalog.header.calls")));
        this.add(new Label("catalogHeaderSuccessFailure", this.t("methods.catalog.header.successFailure")));
        this.add(new Label("catalogHeaderRemoteTimeout", this.t("methods.catalog.header.remoteTimeout")));
        this.add(new Label("catalogHeaderLatency", this.t("methods.catalog.header.latency")));
        this.add(new Label("catalogHeaderLastFailure", this.t("methods.catalog.header.lastFailure")));
        this.add(new Label("catalogHeaderAction", this.t("methods.catalog.header.action")));

        this.add(new ListView<>("serviceRows", this.dashboard().snapshot().services()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminServiceSnapshot> item) {
                final RpcAdminServiceSnapshot service = item.getModelObject();
                item.add(new Label("name", service.name()));
                item.add(new Label("location", service.environmentName() + "/" + service.profileName()));
                item.add(new Label("channelId", service.channelId()));
                item.add(new Label("state", MethodsPage.this.t(service.enabled() ? "state.enabled" : "state.disabled")));
                item.add(new Label("methodCount", UiFormatters.integer(service.methods().size())));
                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.add(new Button("actionButton", Model.of(MethodsPage.this.t(service.enabled() ? "action.disable" : "action.enable"))) {
                    @Override
                    public void onSubmit() {
                        if (service.enabled()) {
                            MethodsPage.this.application().facade().disableService(MethodsPage.this.adminSession(), service.id());
                            MethodsPage.this.info(MethodsPage.this.t("methods.feedback.serviceDisabled") + " " + service.name());
                        } else {
                            MethodsPage.this.application().facade().enableService(MethodsPage.this.adminSession(), service.id());
                            MethodsPage.this.info(MethodsPage.this.t("methods.feedback.serviceEnabled") + " " + service.name());
                        }
                        MethodsPage.this.setResponsePage(MethodsPage.class);
                    }
                });
                item.add(actionForm);
            }
        });
        this.add(new ListView<>("serverRows", this.dashboard().snapshot().servers()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminServerSnapshot> item) {
                final RpcAdminServerSnapshot server = item.getModelObject();
                item.add(new Label("name", server.name()));
                item.add(new Label("location", server.environmentName() + "/" + server.profileName()));
                item.add(new Label("channelId", server.channelId()));
                item.add(new Label("state", MethodsPage.this.t(server.enabled() ? "state.enabled" : "state.disabled")));
                item.add(new Label("requestCount", UiFormatters.integer(server.totalRequests())));
                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.add(new Button("actionButton", Model.of(MethodsPage.this.t(server.enabled() ? "action.disable" : "action.enable"))) {
                    @Override
                    public void onSubmit() {
                        if (server.enabled()) {
                            MethodsPage.this.application().facade().disableServer(MethodsPage.this.adminSession(), server.id());
                            MethodsPage.this.info(MethodsPage.this.t("methods.feedback.serverDisabled") + " " + server.name());
                        } else {
                            MethodsPage.this.application().facade().enableServer(MethodsPage.this.adminSession(), server.id());
                            MethodsPage.this.info(MethodsPage.this.t("methods.feedback.serverEnabled") + " " + server.name());
                        }
                        MethodsPage.this.setResponsePage(MethodsPage.class);
                    }
                });
                item.add(actionForm);
            }
        });
        this.add(new ListView<>("clientRows", this.dashboard().snapshot().clients()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminClientSnapshot> item) {
                final RpcAdminClientSnapshot client = item.getModelObject();
                item.add(new Label("name", client.name()));
                item.add(new Label("location", client.environmentName() + "/" + client.profileName()));
                item.add(new Label("channelId", client.channelId()));
                item.add(new Label("state", MethodsPage.this.t(client.enabled() ? "state.enabled" : "state.disabled")));
                item.add(new Label("callCount", UiFormatters.integer(client.totalCalls())));
                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.add(new Button("actionButton", Model.of(MethodsPage.this.t(client.enabled() ? "action.disable" : "action.enable"))) {
                    @Override
                    public void onSubmit() {
                        if (client.enabled()) {
                            MethodsPage.this.application().facade().disableClient(MethodsPage.this.adminSession(), client.id());
                            MethodsPage.this.info(MethodsPage.this.t("methods.feedback.clientDisabled") + " " + client.name());
                        } else {
                            MethodsPage.this.application().facade().enableClient(MethodsPage.this.adminSession(), client.id());
                            MethodsPage.this.info(MethodsPage.this.t("methods.feedback.clientEnabled") + " " + client.name());
                        }
                        MethodsPage.this.setResponsePage(MethodsPage.class);
                    }
                });
                item.add(actionForm);
            }
        });
        this.add(new ListView<>("methodRows", this.dashboard().methodRows()) {
            @Override
            protected void populateItem(final ListItem<MethodRow> item) {
                final MethodRow row = item.getModelObject();
                item.add(new Label("ownerName", row.ownerName()));
                item.add(new Label("ownerType", MethodsPage.this.t(row.ownerType().messageKey())));
                item.add(new Label("signature", "msg " + row.method().requestMessageTypeId() + " -> " + row.method().responseMessageTypeId()));
                item.add(new Label("state", MethodsPage.this.t(row.method().enabled() ? "state.enabledMethod" : "state.disabledMethod")));
                item.add(new Label("channelId", row.channelId()));
                item.add(new Label("calls", UiFormatters.integer(row.method().totalCalls())));
                item.add(new Label("successFailure", UiFormatters.integer(row.method().successes()) + " / " + UiFormatters.integer(row.method().failures())));
                item.add(new Label("remoteTimeout", UiFormatters.integer(row.method().remoteErrors()) + " / " + UiFormatters.integer(row.method().timeouts())));
                item.add(new Label("latency", UiFormatters.latency(row.method().averageLatencyNs()) + " / " + UiFormatters.latency(row.method().maxLatencyNs())));
                item.add(new Label("lastFailure", row.method().lastFailure() == null || row.method().lastFailure().isBlank()
                        ? MethodsPage.this.t("common.emDash")
                        : row.method().lastFailure()));

                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.setVisible(row.operationAvailable());
                if (row.operationAvailable()) {
                    actionForm.add(new Button("actionButton", Model.of(MethodsPage.this.t(row.method().enabled() ? "action.disable" : "action.enable"))) {
                        @Override
                        public void onSubmit() {
                            if (row.method().enabled()) {
                                MethodsPage.this.application().facade().disableMethod(
                                        MethodsPage.this.adminSession(),
                                        row.ownerId(),
                                        row.method().requestMessageTypeId()
                                );
                                MethodsPage.this.info(MethodsPage.this.t("methods.feedback.methodDisabled") + " " + row.method().name());
                            } else {
                                MethodsPage.this.application().facade().enableMethod(
                                        MethodsPage.this.adminSession(),
                                        row.ownerId(),
                                        row.method().requestMessageTypeId()
                                );
                                MethodsPage.this.info(MethodsPage.this.t("methods.feedback.methodEnabled") + " " + row.method().name());
                            }
                            MethodsPage.this.setResponsePage(MethodsPage.class);
                        }
                    });
                } else {
                    actionForm.add(new WebMarkupContainer("actionButton").setVisible(false));
                }
                item.add(actionForm);

                final Label readonlyLabel = new Label("readonlyLabel", MethodsPage.this.t("methods.catalog.readonly"));
                readonlyLabel.setVisible(!row.operationAvailable());
                item.add(readonlyLabel);
            }
        });
    }

    @Override
    protected String pageTitleKey() {
        return "methods.pageTitle";
    }

    @Override
    protected String pageDescriptionKey() {
        return "methods.pageDescription";
    }
}