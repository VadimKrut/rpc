package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.Model;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminChannelSnapshot;
import ru.pathcreator.pyc.rpc.admin.ui.application.UiFormatters;

public final class ChannelsPage extends BasePage {

    public ChannelsPage() {
        this.add(new ListView<>("channelCards", this.dashboard().snapshot().channels()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminChannelSnapshot> item) {
                final RpcAdminChannelSnapshot channel = item.getModelObject();
                item.add(AttributeModifier.append("class", channel.closed() ? "status-closed" : channel.paused() ? "status-paused" : "status-active"));
                item.add(new Label("headline", channel.environmentName() + "/" + channel.profileName()));
                item.add(new Label("channelName", channel.name()));
                item.add(new Label("runtimeName", channel.runtimeName()));
                item.add(new Label("streamId", "stream " + channel.streamId()));
                item.add(new Label("publicationChannel", channel.publicationChannel()));
                item.add(new Label("subscriptionChannel", channel.subscriptionChannel()));
                item.add(new Label("state", channel.closed() ? "Закрыт" : channel.paused() ? "На паузе" : "Активен"));
                item.add(new Label("traffic", UiFormatters.bytes(channel.bytesIn()) + " / " + UiFormatters.bytes(channel.bytesOut())));
                item.add(new Label("requests", UiFormatters.integer(channel.requestsSent()) + " / " + UiFormatters.integer(channel.responsesReceived())));
                item.add(new Label("timeouts", UiFormatters.integer(channel.publishTimeouts()) + " / " + UiFormatters.integer(channel.callTimeouts())));
                item.add(new Label("waiters", channel.waitersCapacity() == 0
                        ? "0%"
                        : UiFormatters.integer(channel.currentWaiters() * 100L / channel.waitersCapacity()) + "%"));
                item.add(new Label("memory", UiFormatters.bytes(channel.estimatedOwnedMemoryBytes())));
                item.add(new Label("activity", UiFormatters.instant(channel.lastActivityAtEpochMs())));

                final Form<Void> actionForm = new Form<>("actionForm");
                final Button actionButton = new Button("actionButton", channel.paused() ? Model.of("Возобновить") : Model.of("Поставить на паузу")) {
                    @Override
                    public void onSubmit() {
                        if (channel.paused()) {
                            ChannelsPage.this.application().facade().resumeChannel(ChannelsPage.this.adminSession(), channel.id());
                            ChannelsPage.this.info("Канал " + channel.profileName() + " снова переведён в рабочее состояние.");
                        } else {
                            ChannelsPage.this.application().facade().pauseChannel(ChannelsPage.this.adminSession(), channel.id());
                            ChannelsPage.this.info("Канал " + channel.profileName() + " поставлен на паузу.");
                        }
                        ChannelsPage.this.setResponsePage(ChannelsPage.class);
                    }
                };
                actionButton.setEnabled(!channel.closed());
                actionForm.add(actionButton);
                item.add(actionForm);

                final WebMarkupContainer closedHint = new WebMarkupContainer("closedHint");
                closedHint.setVisible(channel.closed());
                item.add(closedHint);
            }
        });
    }

    @Override
    protected String pageTitle() {
        return "Каналы и связность";
    }

    @Override
    protected String pageDescription() {
        return "Экран для transport-настроек, признаков давления на канал, наблюдения за таймаутами, насыщением очереди ожидания и связями с сервисами, серверами и клиентами.";
    }
}