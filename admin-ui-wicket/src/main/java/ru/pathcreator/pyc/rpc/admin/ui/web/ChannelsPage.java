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
        this.add(new Label("sectionTitle", this.t("channels.section.title")));
        this.add(new Label("sectionDescription", this.t("channels.section.description")));
        this.add(new ListView<>("channelCards", this.dashboard().snapshot().channels()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminChannelSnapshot> item) {
                final RpcAdminChannelSnapshot channel = item.getModelObject();
                item.add(AttributeModifier.append("class", channel.closed() ? "status-closed" : channel.paused() ? "status-paused" : "status-active"));
                item.add(new Label("headline", channel.environmentName() + "/" + channel.profileName()));
                item.add(new Label("channelName", channel.name()));
                item.add(new Label("runtimeName", channel.runtimeName()));
                item.add(new Label("streamId", "stream " + channel.streamId()));
                item.add(new Label("publicationLabel", ChannelsPage.this.t("channels.route.publication")));
                item.add(new Label("publicationChannel", channel.publicationChannel()));
                item.add(new Label("subscriptionLabel", ChannelsPage.this.t("channels.route.subscription")));
                item.add(new Label("subscriptionChannel", channel.subscriptionChannel()));
                item.add(new Label("state", ChannelsPage.this.t(channel.closed()
                        ? "state.closed"
                        : channel.paused() ? "state.paused" : "state.active")));
                item.add(new Label("trafficLabel", ChannelsPage.this.t("channels.metrics.traffic")));
                item.add(new Label("traffic", UiFormatters.bytes(channel.bytesIn()) + " / " + UiFormatters.bytes(channel.bytesOut())));
                item.add(new Label("requestsLabel", ChannelsPage.this.t("channels.metrics.requests")));
                item.add(new Label("requests", UiFormatters.integer(channel.requestsSent()) + " / " + UiFormatters.integer(channel.responsesReceived())));
                item.add(new Label("timeoutsLabel", ChannelsPage.this.t("channels.metrics.timeouts")));
                item.add(new Label("timeouts", UiFormatters.integer(channel.publishTimeouts()) + " / " + UiFormatters.integer(channel.callTimeouts())));
                item.add(new Label("waitersLabel", ChannelsPage.this.t("channels.metrics.waiters")));
                item.add(new Label("waiters", channel.waitersCapacity() == 0
                        ? "0%"
                        : UiFormatters.integer(channel.currentWaiters() * 100L / channel.waitersCapacity()) + "%"));
                item.add(new Label("memoryLabel", ChannelsPage.this.t("channels.metrics.memory")));
                item.add(new Label("memory", UiFormatters.bytes(channel.estimatedOwnedMemoryBytes())));
                item.add(new Label("activityLabel", ChannelsPage.this.t("channels.metrics.activity")));
                item.add(new Label("activity", UiFormatters.instant(channel.lastActivityAtEpochMs())));

                final Form<Void> actionForm = new Form<>("actionForm");
                final Button actionButton = new Button("actionButton", Model.of(channel.paused()
                        ? ChannelsPage.this.t("action.resume")
                        : ChannelsPage.this.t("action.pause"))) {
                    @Override
                    public void onSubmit() {
                        if (channel.paused()) {
                            ChannelsPage.this.application().facade().resumeChannel(ChannelsPage.this.adminSession(), channel.id());
                            ChannelsPage.this.info(ChannelsPage.this.t("channels.feedback.resumed") + " " + channel.profileName());
                        } else {
                            ChannelsPage.this.application().facade().pauseChannel(ChannelsPage.this.adminSession(), channel.id());
                            ChannelsPage.this.info(ChannelsPage.this.t("channels.feedback.paused") + " " + channel.profileName());
                        }
                        ChannelsPage.this.setResponsePage(ChannelsPage.class);
                    }
                };
                actionButton.setEnabled(!channel.closed());
                actionForm.add(actionButton);
                item.add(actionForm);

                final WebMarkupContainer closedHint = new WebMarkupContainer("closedHint");
                closedHint.add(new Label("closedHintLabel", ChannelsPage.this.t("channels.closedHint")));
                closedHint.setVisible(channel.closed());
                item.add(closedHint);
            }
        });
    }

    @Override
    protected String pageTitleKey() {
        return "channels.pageTitle";
    }

    @Override
    protected String pageDescriptionKey() {
        return "channels.pageDescription";
    }
}