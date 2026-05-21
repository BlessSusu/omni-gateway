package com.omni.gateway.network.observability;

import com.omni.gateway.core.logging.OmniMdc;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.function.Supplier;

public final class GatewayTracing {

    private GatewayTracing() {
    }

    public static <T> T inSpan(Tracer tracer, String name, Supplier<T> action) {
        if (tracer == null) {
            return action.get();
        }
        Span span = tracer.nextSpan().name(name).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            bindTraceMdc(span);
            return action.get();
        } finally {
            span.end();
            OmniMdc.clearTrace();
        }
    }

    public static void run(Tracer tracer, String name, Runnable action) {
        inSpan(tracer, name, () -> {
            action.run();
            return null;
        });
    }

    public static String currentTraceId(Tracer tracer) {
        if (tracer == null) {
            return null;
        }
        Span current = tracer.currentSpan();
        if (current != null && current.context() != null) {
            return current.context().traceId();
        }
        return null;
    }

    private static void bindTraceMdc(Span span) {
        if (span != null && span.context() != null) {
            OmniMdc.traceId(span.context().traceId());
        }
    }
}
