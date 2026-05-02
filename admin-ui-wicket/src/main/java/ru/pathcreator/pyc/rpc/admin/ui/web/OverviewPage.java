package ru.pathcreator.pyc.rpc.admin.ui.web;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminChannelSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminRuntimeSnapshot;
import ru.pathcreator.pyc.rpc.admin.snapshot.RpcAdminSummarySnapshot;
import ru.pathcreator.pyc.rpc.admin.ui.application.AdminConsoleDashboard;
import ru.pathcreator.pyc.rpc.admin.ui.application.ProcessTelemetrySnapshot;
import ru.pathcreator.pyc.rpc.admin.ui.application.UiFormatters;

import java.io.Serializable;
import java.util.List;

public final class OverviewPage extends BasePage {

    public OverviewPage() {
        final AdminConsoleDashboard dashboard = this.dashboard();
        final RpcAdminSummarySnapshot summary = dashboard.snapshot().summary();
        final ProcessTelemetrySnapshot telemetry = dashboard.telemetry();

        this.add(new Label("summarySectionTitle", this.t("overview.summary.title")));
        this.add(new Label("generatedAtLabel", this.t("overview.summary.generatedAt")));
        this.add(new Label("generatedAt", UiFormatters.instant(dashboard.snapshot().createdAtEpochMs())));
        this.add(new ListView<>("summaryCards", List.of(
                new MetricCard("overview.metrics.runtimes.title", UiFormatters.integer(summary.runtimeCount()), "overview.metrics.runtimes.hint", "primary"),
                new MetricCard("overview.metrics.channels.title", UiFormatters.integer(summary.channelCount()), "overview.metrics.channels.hint", "primary"),
                new MetricCard("overview.metrics.activeChannels.title", UiFormatters.integer(summary.activeChannelCount()), "overview.metrics.activeChannels.hint", "success"),
                new MetricCard("overview.metrics.pausedChannels.title", UiFormatters.integer(summary.pausedChannelCount()), "overview.metrics.pausedChannels.hint", "warning"),
                new MetricCard("overview.metrics.clients.title", UiFormatters.integer(summary.clientCount()), "overview.metrics.clients.hint", "primary"),
                new MetricCard("overview.metrics.servers.title", UiFormatters.integer(summary.serverCount()), "overview.metrics.servers.hint", "primary"),
                new MetricCard("overview.metrics.services.title", UiFormatters.integer(summary.serviceCount()), "overview.metrics.services.hint", "primary")
        )) {
            @Override
            protected void populateItem(final ListItem<MetricCard> item) {
                item.add(AttributeModifier.append("class", "metric-" + item.getModelObject().tone()));
                item.add(new Label("title", OverviewPage.this.t(item.getModelObject().titleKey())));
                item.add(new Label("value", item.getModelObject().value()));
                item.add(new Label("hint", OverviewPage.this.t(item.getModelObject().hintKey())));
            }
        });

        this.add(new Label("telemetrySectionTitle", this.t("overview.telemetry.title")));
        this.add(new Label("telemetrySectionDescription", this.t("overview.telemetry.description")));
        this.add(new ListView<>("telemetryCards", List.of(
                new MetricCard("overview.telemetry.processCpu.title", UiFormatters.percent(telemetry.processCpuLoadPercent()), "overview.telemetry.processCpu.hint", "neutral"),
                new MetricCard("overview.telemetry.systemCpu.title", UiFormatters.percent(telemetry.systemCpuLoadPercent()), "overview.telemetry.systemCpu.hint", "neutral"),
                new MetricCard("overview.telemetry.heapUsed.title", UiFormatters.bytes(telemetry.heapUsedBytes()), "overview.telemetry.heapUsed.hintPrefix:" + UiFormatters.bytes(telemetry.heapCommittedBytes()), "neutral"),
                new MetricCard("overview.telemetry.threads.title", UiFormatters.integer(telemetry.liveThreadCount()), "overview.telemetry.threads.hint", "neutral"),
                new MetricCard("overview.telemetry.uptime.title", UiFormatters.duration(telemetry.uptime()), "overview.telemetry.uptime.hint", "neutral")
        )) {
            @Override
            protected void populateItem(final ListItem<MetricCard> item) {
                item.add(AttributeModifier.append("class", "metric-" + item.getModelObject().tone()));
                item.add(new Label("title", OverviewPage.this.t(item.getModelObject().titleKey())));
                item.add(new Label("value", item.getModelObject().value()));
                final String hintKey = item.getModelObject().hintKey();
                final String hint = hintKey.startsWith("overview.telemetry.heapUsed.hintPrefix:")
                        ? OverviewPage.this.t("overview.telemetry.heapUsed.hintPrefix") + " " + hintKey.substring(hintKey.indexOf(':') + 1)
                        : OverviewPage.this.t(hintKey);
                item.add(new Label("hint", hint));
            }
        });

        this.add(new Label("runtimeSectionTitle", this.t("overview.runtime.title")));
        this.add(new Label("runtimeSectionDescription", this.t("overview.runtime.description")));
        this.add(new Label("runtimeHeaderName", this.t("overview.runtime.header.name")));
        this.add(new Label("runtimeHeaderId", this.t("overview.runtime.header.id")));
        this.add(new Label("runtimeHeaderDirectory", this.t("overview.runtime.header.directory")));
        this.add(new Label("runtimeHeaderState", this.t("overview.runtime.header.state")));
        this.add(new Label("runtimeHeaderChannels", this.t("overview.runtime.header.channels")));
        this.add(new ListView<>("runtimeRows", dashboard.snapshot().runtimes()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminRuntimeSnapshot> item) {
                final RpcAdminRuntimeSnapshot runtime = item.getModelObject();
                item.add(new Label("name", runtime.name()));
                item.add(new Label("runtimeId", runtime.runtimeId()));
                item.add(new Label("aeronDirectory", runtime.aeronDirectoryName()));
                item.add(new Label("closed", OverviewPage.this.t(runtime.closed() ? "state.closed" : "state.running")));
                item.add(new Label("channelCount", UiFormatters.integer(runtime.channelCount())));
            }
        });

        this.add(new Label("channelSectionTitle", this.t("overview.channels.title")));
        this.add(new Label("channelSectionDescription", this.t("overview.channels.description")));
        this.add(new Label("channelHeaderName", this.t("overview.channels.header.name")));
        this.add(new Label("channelHeaderState", this.t("overview.channels.header.state")));
        this.add(new Label("channelHeaderTraffic", this.t("overview.channels.header.traffic")));
        this.add(new Label("channelHeaderTimeouts", this.t("overview.channels.header.timeouts")));
        this.add(new Label("channelHeaderWaiters", this.t("overview.channels.header.waiters")));
        this.add(new Label("channelHeaderActivity", this.t("overview.channels.header.activity")));
        this.add(new ListView<>("channelRows", dashboard.snapshot().channels()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminChannelSnapshot> item) {
                final RpcAdminChannelSnapshot channel = item.getModelObject();
                item.add(new Label("name", channel.environmentName() + "/" + channel.profileName()));
                item.add(new Label("state", OverviewPage.this.t(channel.closed()
                        ? "state.closed"
                        : channel.paused() ? "state.paused" : "state.active")));
                item.add(new Label("traffic", UiFormatters.bytes(channel.bytesIn()) + " / " + UiFormatters.bytes(channel.bytesOut())));
                item.add(new Label("timeouts", UiFormatters.integer(channel.publishTimeouts()) + " / " + UiFormatters.integer(channel.callTimeouts())));
                item.add(new Label("waiters", channel.waitersCapacity() == 0
                        ? OverviewPage.this.t("common.notAvailable")
                        : UiFormatters.integer(channel.currentWaiters()) + " / " + UiFormatters.integer(channel.waitersCapacity())));
                item.add(new Label("activity", UiFormatters.instant(channel.lastActivityAtEpochMs())));
            }
        });
    }

    @Override
    protected String pageTitleKey() {
        return "overview.pageTitle";
    }

    @Override
    protected String pageDescriptionKey() {
        return "overview.pageDescription";
    }

    private record MetricCard(String titleKey, String value, String hintKey, String tone) implements Serializable {
    }
}