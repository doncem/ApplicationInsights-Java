/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.internal.processor;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import com.microsoft.applicationinsights.extensibility.TelemetryProcessor;
import com.microsoft.applicationinsights.internal.annotation.BuiltInProcessor;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.util.LocalStringsUtils;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * The class can filter out RequestTelemetries that
 * have a duration which is less than a predefined value
 * have http codes that are not needed based on configuration
 * <p>
 * Illegal value will prevent from the filter from being used.
 * <p>
 * Created by gupele on 7/26/2016.
 */
@BuiltInProcessor("RequestTelemetryFilter")
public final class RequestTelemetryFilter implements TelemetryProcessor {
    private final class FromTo {
        public final int from;
        public final int to;

        private FromTo(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    private long minimumDurationInMS = 0;
    private boolean hasBlocked;
    private final Set<String> exactBadResponseCodes = new HashSet<String>();
    private final List<FromTo> ignoredResponseCodeRange = new ArrayList<FromTo>();

    public RequestTelemetryFilter() {
    }

    @Override
    public boolean process(Telemetry telemetry) {
        if (telemetry == null) {
            return true;
        }

        if (!hasBlocked && minimumDurationInMS <= 0) {
            return true;
        }

        if (telemetry instanceof RequestTelemetry) {
            RequestTelemetry requestTelemetry = (RequestTelemetry) telemetry;
            String responseCode = requestTelemetry.getResponseCode();

            if (exactBadResponseCodes.contains(requestTelemetry.getResponseCode())) {
                return false;
            }

            int asInt = Integer.valueOf(responseCode);
            for (FromTo fromTo : ignoredResponseCodeRange) {
                if (fromTo.from <= asInt && fromTo.to >= asInt) {
                    return false;
                }
            }

            Duration requestDuration = requestTelemetry.getDuration();
            if (requestDuration != null && requestDuration.getTotalMilliseconds() < minimumDurationInMS) {
                return false;
            }

            if (LocalStringsUtils.isNullOrEmpty(requestTelemetry.getResponseCode())) {
                return true;
            }
        }

        return true;
    }

    public void setMinimumDurationInMS(String minimumDurationInMS) throws Throwable {
        try {
            this.minimumDurationInMS = Long.valueOf(minimumDurationInMS);
            InternalLogger.INSTANCE.trace("RequestTelemetryFilter: successfully set MinimumDurationInMS = %d", this.minimumDurationInMS);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                InternalLogger.INSTANCE.error("RequestTelemetryFilter: failed to set minimum duration: %s, Exception : %s", minimumDurationInMS
                        , ExceptionUtils.getStackTrace(t));
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
            throw t;
        }
    }

    public void setNotNeededResponseCodes(String notNeededResponseCodes) throws Throwable {
        try {
            if (LocalStringsUtils.isNullOrEmpty(notNeededResponseCodes)) {
                hasBlocked = false;
            } else {
                List<String> exclusions = Arrays.asList(notNeededResponseCodes.split(","));
                for (String ex : exclusions) {
                    ex = ex.trim();
                    if (LocalStringsUtils.isNullOrEmpty(ex)) {
                        continue;
                    }

                    List<String> fromTo = Arrays.asList(ex.split("-"));
                    if (fromTo.size() == 1) {
                        exactBadResponseCodes.add(ex);
                        continue;
                    }
                    if (fromTo.size() != 2) {
                        continue;
                    }
                    if (LocalStringsUtils.isNullOrEmpty(fromTo.get(0)) || LocalStringsUtils.isNullOrEmpty(fromTo.get(1))) {
                        continue;
                    }
                    int f = Integer.valueOf(fromTo.get(0));
                    int t = Integer.valueOf(fromTo.get(1));
                    ignoredResponseCodeRange.add(new FromTo(f, t));
                }
                hasBlocked = !exactBadResponseCodes.isEmpty() || !ignoredResponseCodeRange.isEmpty();
            }

            InternalLogger.INSTANCE.trace(String.format("ResponseCodeFilter: successfully set non needed response codes: %s", notNeededResponseCodes));
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                InternalLogger.INSTANCE.error("RequestTelemetryFilter: failed to parse NotNeededResponseCodes: %s, " +
                        "Exception : %s", notNeededResponseCodes, ExceptionUtils.getStackTrace(t));
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
            throw t;
        }
    }
}
