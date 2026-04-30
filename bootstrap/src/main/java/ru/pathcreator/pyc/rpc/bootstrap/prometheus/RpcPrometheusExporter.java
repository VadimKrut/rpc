package ru.pathcreator.pyc.rpc.bootstrap.prometheus;

import ru.pathcreator.pyc.rpc.observability.client.RpcClientMethodMetricsSnapshot;
import ru.pathcreator.pyc.rpc.observability.client.RpcClientMetricsSnapshot;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMethodMetricsSnapshot;
import ru.pathcreator.pyc.rpc.observability.server.RpcServerMetricsSnapshot;

public final class RpcPrometheusExporter {

    private RpcPrometheusExporter() {
    }

    public static String clientMetrics(
            final RpcClientMetricsSnapshot snapshot
    ) {
        final StringBuilder text = new StringBuilder(1024);
        for (final RpcClientMethodMetricsSnapshot method : snapshot.methods()) {
            appendCounter(text, "rpc_client_calls_total", method.methodName(), method.calls());
            appendCounter(text, "rpc_client_successes_total", method.methodName(), method.successes());
            appendCounter(text, "rpc_client_remote_errors_total", method.methodName(), method.remoteErrors());
            appendCounter(text, "rpc_client_timeouts_total", method.methodName(), method.timeouts());
            appendCounter(text, "rpc_client_local_failures_total", method.methodName(), method.localFailures());
            appendGauge(text, "rpc_client_in_flight", method.methodName(), method.inFlight());
            appendGauge(text, "rpc_client_max_in_flight", method.methodName(), method.maxInFlight());
            appendCounter(text, "rpc_client_total_latency_ns", method.methodName(), method.totalLatencyNs());
            appendGauge(text, "rpc_client_max_latency_ns", method.methodName(), method.maxLatencyNs());
            appendCounter(text, "rpc_client_total_response_payload_bytes", method.methodName(), method.totalResponsePayloadBytes());
            appendCounter(text, "rpc_client_remote_client_errors_total", method.methodName(), method.remoteClientErrors());
            appendCounter(text, "rpc_client_remote_server_errors_total", method.methodName(), method.remoteServerErrors());
            appendCounter(text, "rpc_client_remote_other_errors_total", method.methodName(), method.remoteOtherErrors());
        }
        return text.toString();
    }

    public static String serverMetrics(
            final RpcServerMetricsSnapshot snapshot
    ) {
        final StringBuilder text = new StringBuilder(1024);
        for (final RpcServerMethodMetricsSnapshot method : snapshot.methods()) {
            appendCounter(text, "rpc_server_requests_total", method.methodName(), method.requests());
            appendCounter(text, "rpc_server_successes_total", method.methodName(), method.successes());
            appendCounter(text, "rpc_server_failures_total", method.methodName(), method.failures());
            appendCounter(text, "rpc_server_client_errors_total", method.methodName(), method.clientErrors());
            appendCounter(text, "rpc_server_server_errors_total", method.methodName(), method.serverErrors());
            appendCounter(text, "rpc_server_other_errors_total", method.methodName(), method.otherErrors());
            appendGauge(text, "rpc_server_in_flight", method.methodName(), method.inFlight());
            appendGauge(text, "rpc_server_max_in_flight", method.methodName(), method.maxInFlight());
            appendCounter(text, "rpc_server_total_latency_ns", method.methodName(), method.totalLatencyNs());
            appendGauge(text, "rpc_server_max_latency_ns", method.methodName(), method.maxLatencyNs());
            appendCounter(text, "rpc_server_total_request_payload_bytes", method.methodName(), method.totalRequestPayloadBytes());
            appendCounter(text, "rpc_server_total_response_payload_bytes", method.methodName(), method.totalResponsePayloadBytes());
        }
        return text.toString();
    }

    private static void appendCounter(
            final StringBuilder text,
            final String metric,
            final String methodName,
            final long value
    ) {
        append(text, metric, methodName, value);
    }

    private static void appendGauge(
            final StringBuilder text,
            final String metric,
            final String methodName,
            final long value
    ) {
        append(text, metric, methodName, value);
    }

    private static void append(
            final StringBuilder text,
            final String metric,
            final String methodName,
            final long value
    ) {
        text.append(metric)
                .append("{method=\"")
                .append(escapeLabel(methodName))
                .append("\"} ")
                .append(value)
                .append('\n');
    }

    private static String escapeLabel(
            final String value
    ) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}