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
        this.add(new ListView<>("serviceRows", this.dashboard().snapshot().services()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminServiceSnapshot> item) {
                final RpcAdminServiceSnapshot service = item.getModelObject();
                item.add(new Label("name", service.name()));
                item.add(new Label("location", service.environmentName() + "/" + service.profileName()));
                item.add(new Label("channelId", service.channelId()));
                item.add(new Label("state", service.enabled() ? "Включен" : "Выключен"));
                item.add(new Label("methodCount", UiFormatters.integer(service.methods().size())));
                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.add(new Button("actionButton", service.enabled() ? Model.of("Отключить") : Model.of("Включить")) {
                    @Override
                    public void onSubmit() {
                        if (service.enabled()) {
                            MethodsPage.this.application().facade().disableService(MethodsPage.this.adminSession(), service.id());
                            MethodsPage.this.info("Сервис " + service.name() + " отключён.");
                        } else {
                            MethodsPage.this.application().facade().enableService(MethodsPage.this.adminSession(), service.id());
                            MethodsPage.this.info("Сервис " + service.name() + " включён.");
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
                item.add(new Label("state", server.enabled() ? "Включен" : "Выключен"));
                item.add(new Label("requestCount", UiFormatters.integer(server.totalRequests())));
                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.add(new Button("actionButton", server.enabled() ? Model.of("Отключить") : Model.of("Включить")) {
                    @Override
                    public void onSubmit() {
                        if (server.enabled()) {
                            MethodsPage.this.application().facade().disableServer(MethodsPage.this.adminSession(), server.id());
                            MethodsPage.this.info("Сервер " + server.name() + " отключён.");
                        } else {
                            MethodsPage.this.application().facade().enableServer(MethodsPage.this.adminSession(), server.id());
                            MethodsPage.this.info("Сервер " + server.name() + " включён.");
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
                item.add(new Label("state", client.enabled() ? "Включен" : "Выключен"));
                item.add(new Label("callCount", UiFormatters.integer(client.totalCalls())));
                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.add(new Button("actionButton", client.enabled() ? Model.of("Отключить") : Model.of("Включить")) {
                    @Override
                    public void onSubmit() {
                        if (client.enabled()) {
                            MethodsPage.this.application().facade().disableClient(MethodsPage.this.adminSession(), client.id());
                            MethodsPage.this.info("Клиент " + client.name() + " отключён.");
                        } else {
                            MethodsPage.this.application().facade().enableClient(MethodsPage.this.adminSession(), client.id());
                            MethodsPage.this.info("Клиент " + client.name() + " включён.");
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
                item.add(new Label("ownerType", row.ownerType().displayName()));
                item.add(new Label("signature", "msg " + row.method().requestMessageTypeId() + " -> " + row.method().responseMessageTypeId()));
                item.add(new Label("state", row.method().enabled() ? "Включено" : "Выключено"));
                item.add(new Label("channelId", row.channelId()));
                item.add(new Label("calls", UiFormatters.integer(row.method().totalCalls())));
                item.add(new Label("successFailure", UiFormatters.integer(row.method().successes()) + " / " + UiFormatters.integer(row.method().failures())));
                item.add(new Label("remoteTimeout", UiFormatters.integer(row.method().remoteErrors()) + " / " + UiFormatters.integer(row.method().timeouts())));
                item.add(new Label("latency", UiFormatters.latency(row.method().averageLatencyNs()) + " / " + UiFormatters.latency(row.method().maxLatencyNs())));
                item.add(new Label("lastFailure", row.method().lastFailure() == null || row.method().lastFailure().isBlank() ? "—" : row.method().lastFailure()));

                final Form<Void> actionForm = new Form<>("actionForm");
                actionForm.setVisible(row.operationAvailable());
                if (row.operationAvailable()) {
                    actionForm.add(new Button("actionButton", row.method().enabled() ? Model.of("Отключить") : Model.of("Включить")) {
                        @Override
                        public void onSubmit() {
                            if (row.method().enabled()) {
                                MethodsPage.this.application().facade().disableMethod(
                                        MethodsPage.this.adminSession(),
                                        row.ownerId(),
                                        row.method().requestMessageTypeId()
                                );
                                MethodsPage.this.info("Метод " + row.method().name() + " отключён.");
                            } else {
                                MethodsPage.this.application().facade().enableMethod(
                                        MethodsPage.this.adminSession(),
                                        row.ownerId(),
                                        row.method().requestMessageTypeId()
                                );
                                MethodsPage.this.info("Метод " + row.method().name() + " включён.");
                            }
                            MethodsPage.this.setResponsePage(MethodsPage.class);
                        }
                    });
                } else {
                    actionForm.add(new WebMarkupContainer("actionButton").setVisible(false));
                }
                item.add(actionForm);

                final Label readonlyLabel = new Label("readonlyLabel", "Только чтение");
                readonlyLabel.setVisible(!row.operationAvailable());
                item.add(readonlyLabel);
            }
        });
    }

    @Override
    protected String pageTitle() {
        return "Сервисы, контуры и методы";
    }

    @Override
    protected String pageDescription() {
        return "Управление реестром клиентов, серверов и сервисов, а также аудит вызовов методов. Клиентская латентность показывает полный round-trip, серверная — только время обработки на стороне исполнителя.";
    }
}