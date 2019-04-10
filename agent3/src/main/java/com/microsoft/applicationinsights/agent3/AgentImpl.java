package com.microsoft.applicationinsights.agent3;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.agent3.dev.DevLogger;
import com.microsoft.applicationinsights.agent3.model.IncomingSpanImpl;
import com.microsoft.applicationinsights.agent3.model.NopThreadContext;
import com.microsoft.applicationinsights.agent3.model.NopThreadSpan;
import com.microsoft.applicationinsights.agent3.model.ThreadContextImpl;
import com.microsoft.applicationinsights.agent3.utils.Global;
import com.microsoft.applicationinsights.channel.concrete.TelemetryChannelBase;
import com.microsoft.applicationinsights.channel.concrete.inprocess.InProcessTelemetryChannel;
import com.microsoft.applicationinsights.extensibility.ContextInitializer;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.extensibility.initializer.CloudInfoContextInitializer;
import com.microsoft.applicationinsights.extensibility.initializer.DeviceInfoContextInitializer;
import com.microsoft.applicationinsights.extensibility.initializer.SdkVersionContextInitializer;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebCloudRoleTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebOperationIdTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebOperationNameTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebSessionTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebUserAgentTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebUserTelemetryInitializer;
import com.microsoft.applicationinsights.web.internal.RequestTelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtilsCore;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtilsCore.RequestHeaderGetter;
import com.microsoft.applicationinsights.web.internal.correlation.TraceContextCorrelationCore;
import org.glowroot.xyzzy.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.xyzzy.engine.spi.AgentSPI;
import org.glowroot.xyzzy.instrumentation.api.Getter;
import org.glowroot.xyzzy.instrumentation.api.MessageSupplier;
import org.glowroot.xyzzy.instrumentation.api.Span;
import org.glowroot.xyzzy.instrumentation.api.TimerName;

public class AgentImpl implements AgentSPI {

    private final TelemetryClient client;

    private static final DevLogger out = new DevLogger(AgentImpl.class);

    AgentImpl() {
        // FIXME hard coded configuration for smoke tests
        TelemetryConfiguration configuration = TelemetryConfiguration.getActive();
        configuration.setInstrumentationKey("00000000-0000-0000-0000-cba987654321");
        configuration.setChannel(new InProcessTelemetryChannel(Collections
                .singletonMap(TelemetryChannelBase.ENDPOINT_ADDRESS_NAME, "http://fakeingestion:60606/v2/track")));

        // FIXME this should go away once configuration is read from xml
        List<ContextInitializer> contextInitializers = configuration.getContextInitializers();
        contextInitializers.add(new SdkVersionContextInitializer());
        contextInitializers.add(new DeviceInfoContextInitializer());
        contextInitializers.add(new CloudInfoContextInitializer());

        List<TelemetryInitializer> telemetryInitializers = configuration.getTelemetryInitializers();
        telemetryInitializers.add(new WebCloudRoleTelemetryInitializer());
        telemetryInitializers.add(new WebOperationIdTelemetryInitializer());
        telemetryInitializers.add(new WebOperationNameTelemetryInitializer());
        telemetryInitializers.add(new WebSessionTelemetryInitializer());
        telemetryInitializers.add(new WebUserTelemetryInitializer());
        telemetryInitializers.add(new WebUserAgentTelemetryInitializer());

        client = new TelemetryClient();
        client.trackEvent("Agent3 Init");
        out.info("tracked init event");
    }

    @Override
    public <C> Span startIncomingSpan(String transactionType, String transactionName, Getter<C> getter, C carrier,
                                      MessageSupplier messageSupplier, TimerName timerName,
                                      ThreadContextThreadLocal.Holder threadContextHolder, int rootNestingGroupId,
                                      int rootSuppressionKeyId) {

        if (!transactionType.equals("Web")) {
            // this is a little more complicated than desired, but part of the contract of startIncomingSpan is that it
            // sets a ThreadContext in the threadContextHolder before returning, and NopThreadSpan makes sure to clear
            // the threadContextHolder at the end of the thread
            NopThreadSpan nopThreadSpan = new NopThreadSpan(threadContextHolder);
            threadContextHolder.set(new NopThreadContext(null, rootNestingGroupId, rootSuppressionKeyId));
            return nopThreadSpan;
        }

        long startTimeMillis = System.currentTimeMillis();

        RequestTelemetryContext telemetryContext = new RequestTelemetryContext(startTimeMillis);
        ThreadContext.setRequestTelemetryContext(telemetryContext);

        RequestTelemetry requestTelemetry = telemetryContext.getHttpRequestTelemetry();

        requestTelemetry.setName(transactionName);
        requestTelemetry.setTimestamp(new Date(startTimeMillis));

        String userAgent = (String) getter.get(carrier, "User-Agent");
        requestTelemetry.getContext().getUser().setUserAgent(userAgent);

        if (Global.isW3CEnabled) {
            // TODO eliminate wrapper object instantiation
            TraceContextCorrelationCore.resolveCorrelationForRequest(carrier, new RequestHeaderGetterImpl<C>(getter),
                    requestTelemetry);
        } else {
            // TODO eliminate wrapper object instantiation
            TelemetryCorrelationUtilsCore.resolveCorrelationForRequest(carrier, new RequestHeaderGetterImpl<C>(getter),
                    requestTelemetry);
        }

        IncomingSpanImpl incomingSpan = new IncomingSpanImpl(messageSupplier, threadContextHolder, startTimeMillis,
                requestTelemetry, client);

        ThreadContextImpl mainThreadContext = new ThreadContextImpl(threadContextHolder, incomingSpan, telemetryContext,
                rootNestingGroupId, rootSuppressionKeyId, false, client);
        threadContextHolder.set(mainThreadContext);

        return incomingSpan;
    }

    private static class RequestHeaderGetterImpl<Req> implements RequestHeaderGetter<Req> {

        private final Getter<Req> getter;

        private RequestHeaderGetterImpl(Getter<Req> getter) {
            this.getter = getter;
        }

        @Override
        public String getFirst(Req request, String name) {
            return getter.get(request, name);
        }

        @Override
        public Enumeration<String> getAll(Req request, String name) {
            String value = getter.get(request, name);
            if (value == null) {
                return Collections.emptyEnumeration();
            } else {
                return Collections.enumeration(Arrays.asList(value));
            }
        }
    }
}
