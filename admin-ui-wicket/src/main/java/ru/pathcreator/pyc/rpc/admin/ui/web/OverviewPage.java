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

import java.util.List;

public final class OverviewPage extends BasePage {

    public OverviewPage() {
        final AdminConsoleDashboard dashboard = this.dashboard();
        final RpcAdminSummarySnapshot summary = dashboard.snapshot().summary();
        final ProcessTelemetrySnapshot telemetry = dashboard.telemetry();

        this.add(new Label("generatedAt", UiFormatters.instant(dashboard.snapshot().createdAtEpochMs())));
        this.add(new ListView<>("summaryCards", List.of(
                new MetricCard("Контуры выполнения", UiFormatters.integer(summary.runtimeCount()), "Активные экземпляры runtime", "primary"),
                new MetricCard("Каналы", UiFormatters.integer(summary.channelCount()), "Всего каналов под наблюдением", "primary"),
                new MetricCard("Каналы в работе", UiFormatters.integer(summary.activeChannelCount()), "Готовы принимать и отправлять трафик", "success"),
                new MetricCard("Каналы на паузе", UiFormatters.integer(summary.pausedChannelCount()), "Остановлены оператором", "warning"),
                new MetricCard("Клиенты", UiFormatters.integer(summary.clientCount()), "Прокси и ручные клиентские контуры", "primary"),
                new MetricCard("Серверы", UiFormatters.integer(summary.serverCount()), "Серверные регистрации", "primary"),
                new MetricCard("Сервисы", UiFormatters.integer(summary.serviceCount()), "Экспортированные RPC-сервисы", "primary")
        )) {
            @Override
            protected void populateItem(final ListItem<MetricCard> item) {
                item.add(AttributeModifier.append("class", "metric-" + item.getModelObject().tone()));
                item.add(new Label("title", item.getModelObject().title()));
                item.add(new Label("value", item.getModelObject().value()));
                item.add(new Label("hint", item.getModelObject().hint()));
            }
        });
        this.add(new ListView<>("telemetryCards", List.of(
                new MetricCard("CPU процесса", UiFormatters.percent(telemetry.processCpuLoadPercent()), "Загрузка JVM-процесса", "neutral"),
                new MetricCard("CPU узла", UiFormatters.percent(telemetry.systemCpuLoadPercent()), "Суммарная загрузка машины", "neutral"),
                new MetricCard("Heap занят", UiFormatters.bytes(telemetry.heapUsedBytes()), "Из " + UiFormatters.bytes(telemetry.heapCommittedBytes()), "neutral"),
                new MetricCard("Потоки JVM", UiFormatters.integer(telemetry.liveThreadCount()), "Количество активных потоков", "neutral"),
                new MetricCard("Время работы", UiFormatters.duration(telemetry.uptime()), "С момента старта процесса", "neutral")
        )) {
            @Override
            protected void populateItem(final ListItem<MetricCard> item) {
                item.add(AttributeModifier.append("class", "metric-" + item.getModelObject().tone()));
                item.add(new Label("title", item.getModelObject().title()));
                item.add(new Label("value", item.getModelObject().value()));
                item.add(new Label("hint", item.getModelObject().hint()));
            }
        });
        this.add(new ListView<>("runtimeRows", dashboard.snapshot().runtimes()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminRuntimeSnapshot> item) {
                final RpcAdminRuntimeSnapshot runtime = item.getModelObject();
                item.add(new Label("name", runtime.name()));
                item.add(new Label("runtimeId", runtime.runtimeId()));
                item.add(new Label("aeronDirectory", runtime.aeronDirectoryName()));
                item.add(new Label("closed", runtime.closed() ? "Закрыт" : "Работает"));
                item.add(new Label("channelCount", UiFormatters.integer(runtime.channelCount())));
            }
        });
        this.add(new ListView<>("channelRows", dashboard.snapshot().channels()) {
            @Override
            protected void populateItem(final ListItem<RpcAdminChannelSnapshot> item) {
                final RpcAdminChannelSnapshot channel = item.getModelObject();
                item.add(new Label("name", channel.environmentName() + "/" + channel.profileName()));
                item.add(new Label("state", channel.closed() ? "Закрыт" : channel.paused() ? "На паузе" : "Активен"));
                item.add(new Label("traffic", UiFormatters.bytes(channel.bytesIn()) + " / " + UiFormatters.bytes(channel.bytesOut())));
                item.add(new Label("timeouts", UiFormatters.integer(channel.publishTimeouts()) + " / " + UiFormatters.integer(channel.callTimeouts())));
                item.add(new Label("waiters", channel.waitersCapacity() == 0
                        ? "Н/Д"
                        : UiFormatters.integer(channel.currentWaiters()) + " из " + UiFormatters.integer(channel.waitersCapacity())));
                item.add(new Label("activity", UiFormatters.instant(channel.lastActivityAtEpochMs())));
            }
        });
    }

    @Override
    protected String pageTitle() {
        return "Операционный обзор";
    }

    @Override
    protected String pageDescription() {
        return "Главный экран для текущего состояния среды, ресурсоёмкости процесса, эксплуатационных признаков перегрузки и связности каналов в едином контуре наблюдения.";
    }

    private record MetricCard(String title, String value, String hint, String tone) {
    }
}