package io.github.vfedoriv.graphrag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HistoricalSupportApiContractTest {
    @Test
    void movedRecordsRetainEveryHistoricalFieldAndItsOrder() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/support/historical-api-record-fields.json")) {
            assertThat(input).isNotNull();
            JsonNode fixtures = new ObjectMapper().readTree(input);
            for (JsonNode fixture : fixtures) {
                Class<?> type = Class.forName(fixture.get("className").asText());
                List<String> expected = new ObjectMapper().convertValue(fixture.get("components"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList())
                    .as(type.getName()).containsExactlyElementsOf(expected);
            }
        }
    }
}
