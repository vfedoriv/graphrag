package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LangfuseGarageComposeConfigurationTest {

    private static final Path COMPOSE = Path.of("compose.yaml");

    @Test
    void langfuseProfileUsesPinnedInitializedGarage() throws IOException {
        String compose = Files.readString(COMPOSE);

        assertThat(compose)
            .contains("langfuse-garage:")
            .contains("image: 'dxflrs/garage:v2.3.0'")
            .contains("profiles: ['langfuse']")
            .contains("'langfuse_garage_meta:/var/lib/garage/meta'")
            .contains("'langfuse_garage_data:/var/lib/garage/data'")
            .contains("'127.0.0.1:9090:3900'")
            .contains("test: ['CMD', '/garage', 'status']")
            .contains("langfuse-garage-init:")
            .contains("condition: service_completed_successfully")
            .contains("LANGFUSE_GARAGE_NODE_CAPACITY: '${LANGFUSE_GARAGE_NODE_CAPACITY:-10G}'")
            .contains("langfuse_garage_meta:")
            .contains("langfuse_garage_data:");
    }

    @Test
    void langfuseUsesGarageEndpointsAndCredentials() throws IOException {
        String compose = Files.readString(COMPOSE);

        assertThat(compose)
            .contains("LANGFUSE_S3_EVENT_UPLOAD_REGION: '${LANGFUSE_GARAGE_REGION:-garage}'")
            .contains("LANGFUSE_S3_EVENT_UPLOAD_ENDPOINT: 'http://langfuse-garage:3900'")
            .contains("LANGFUSE_S3_EVENT_UPLOAD_FORCE_PATH_STYLE: 'true'")
            .contains("LANGFUSE_S3_EVENT_UPLOAD_ACCESS_KEY_ID: '${LANGFUSE_GARAGE_ACCESS_KEY:-garage-local}'")
            .contains("LANGFUSE_S3_EVENT_UPLOAD_SECRET_ACCESS_KEY: '${LANGFUSE_GARAGE_SECRET_KEY:-garage-local-secret}'")
            .contains("LANGFUSE_S3_MEDIA_UPLOAD_ENDPOINT: '${LANGFUSE_GARAGE_MEDIA_ENDPOINT:-http://localhost:9090}'")
            .contains("LANGFUSE_S3_MEDIA_UPLOAD_FORCE_PATH_STYLE: 'true'")
            .contains("LANGFUSE_S3_MEDIA_UPLOAD_ACCESS_KEY_ID: '${LANGFUSE_GARAGE_ACCESS_KEY:-garage-local}'")
            .contains("LANGFUSE_S3_MEDIA_UPLOAD_SECRET_ACCESS_KEY: '${LANGFUSE_GARAGE_SECRET_KEY:-garage-local-secret}'")
            .contains("LANGFUSE_GARAGE_ACCESS_KEY: '${LANGFUSE_GARAGE_ACCESS_KEY:-garage-local}'")
            .contains("LANGFUSE_GARAGE_SECRET_KEY: '${LANGFUSE_GARAGE_SECRET_KEY:-garage-local-secret}'");
    }

    @Test
    void garageSmokeTestCoversAuthorizedObjectOperationsAndPersistence() throws IOException {
        String smokeCompose = Files.readString(Path.of("compose.garage-smoke.yaml"));
        String smokeScript = Files.readString(Path.of("scripts/garage-smoke-test.sh"));

        assertThat(smokeCompose)
            .contains("image: 'rclone/rclone:1.74.4'")
            .contains("condition: service_healthy")
            .contains("condition: service_completed_successfully")
            .contains("RCLONE_CONFIG_GARAGE_ENDPOINT: 'http://langfuse-garage:3900'")
            .contains("RCLONE_CONFIG_GARAGE_ACCESS_KEY_ID: '${LANGFUSE_GARAGE_ACCESS_KEY:-garage-local}'")
            .contains("RCLONE_CONFIG_GARAGE_SECRET_ACCESS_KEY: '${LANGFUSE_GARAGE_SECRET_KEY:-garage-local-secret}'");
        assertThat(smokeScript)
            .contains("run --rm langfuse-garage-init")
            .contains("rcat \"$object_path\"")
            .contains("cat \"$object_path\"")
            .contains("lsf garage:langfuse/smoke --files-only")
            .contains("up -d --force-recreate --wait langfuse-garage")
            .contains("deletefile \"$object_path\"");
    }
}
