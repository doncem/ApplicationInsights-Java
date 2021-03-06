/*
 * AppInsights-Java
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

package com.microsoft.applicationinsights.internal.channel.common;

import org.junit.Test;

import static org.junit.Assert.*;

public final class TransmissionPolicyStateTest {
    @Test
    public void testStateAfterCtor() {
        TransmissionPolicyState tested = new TransmissionPolicyState();
        assertEquals(TransmissionPolicy.UNBLOCKED, tested.getCurrentState());
    }

    @Test
    public void testSetCurrentState() {
        TransmissionPolicyState tested = new TransmissionPolicyState();
        tested.setCurrentState(TransmissionPolicy.BLOCKED_BUT_CAN_BE_PERSISTED);
        assertEquals(TransmissionPolicy.BLOCKED_BUT_CAN_BE_PERSISTED, tested.getCurrentState());
        tested.setCurrentState(TransmissionPolicy.BLOCKED_AND_CANNOT_BE_PERSISTED);
        assertEquals(TransmissionPolicy.BLOCKED_AND_CANNOT_BE_PERSISTED, tested.getCurrentState());
    }
}
