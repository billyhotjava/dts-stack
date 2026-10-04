package com.yuzhi.dts.copilot.analytics.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotAgentChatClientTest {

    @Test
    void describeStreamTransportFailureFallsBackWhenIOExceptionHasNoMessage() {
        assertThat(CopilotAgentChatClient.describeStreamTransportFailure(new IOException()))
                .isEqualTo("stream transport error");
    }

    @Test
    void describeStreamTransportFailureKeepsMeaningfulIOExceptionMessage() {
        assertThat(CopilotAgentChatClient.describeStreamTransportFailure(new IOException("Connection reset")))
                .isEqualTo("Connection reset");
    }
}
