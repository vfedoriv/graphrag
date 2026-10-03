package io.github.vfedoriv.graphrag.application.settings;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingCodecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeSettingCodecsTest {

    private final RuntimeSettingCodecs codecs = new RuntimeSettingCodecs();

    @Test
    void parsesTypedValuesWithoutInfrastructureDependencies() {
        assertThat(codecs.integer("count", "3", 1)).isEqualTo(3);
        assertThat(codecs.bool("enabled", "TRUE")).isTrue();
        assertThat(codecs.stringList("keywords", "CREATE, MERGE")).containsExactly("CREATE", "MERGE");
        assertThat(codecs.loggingLevel("logging.level.root", "warn")).isEqualTo("WARN");
        assertThat(codecs.masked("secret")).isEqualTo(java.util.Map.of("configured", true, "masked", true));
    }

    @Test
    void rejectsInvalidOrEmptyValues() {
        assertThatThrownBy(() -> codecs.integer("count", 0, 1)).hasMessageContaining("greater than or equal to 1");
        assertThatThrownBy(() -> codecs.bool("enabled", "sometimes")).hasMessageContaining("boolean");
        assertThatThrownBy(() -> codecs.stringList("keywords", List.of())).hasMessageContaining("at least one value");
        assertThatThrownBy(() -> codecs.nonBlankString("path", " ")).hasMessageContaining("must not be blank");
    }
}
