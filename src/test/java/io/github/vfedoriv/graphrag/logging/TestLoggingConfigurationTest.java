package io.github.vfedoriv.graphrag.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class TestLoggingConfigurationTest {

    private static final Logger LOG = LoggerFactory.getLogger(TestLoggingConfigurationTest.class);

    @Test
    void applicationWarningsAndErrorsRemainVisible(CapturedOutput output) {
        LOG.warn("application warning remains visible");
        LOG.error("application error remains visible");

        assertThat(output).contains("application warning remains visible", "application error remains visible");
    }
}
