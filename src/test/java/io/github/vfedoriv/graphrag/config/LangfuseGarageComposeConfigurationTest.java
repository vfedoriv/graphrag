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
            .contains("'langfuse_garage_meta:/var/lib/garage/meta'")
            .contains("'langfuse_garage_data:/var/lib/garage/data'")
            .contains("'127.0.0.1:9090:3900'")
            .contains("test: ['CMD', '/garage', 'status']")
            .contains("langfuse-garage-init:")
            .contains("condition: service_completed_successfully")
            .contains("LANGFUSE_GARAGE_NODE_CAPACITY: '${LANGFUSE_GARAGE_NODE_CAPACITY:-10G}'");
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
            .contains("LANGFUSE_GARAGE_SECRET_KEY: '${LANGFUSE_GARAGE_SECRET_KEY:-garage-local-secret}'")
            .doesNotContain("langfuse-minio:")
            .doesNotContain("LANGFUSE_MINIO_ROOT_")
            .doesNotContain("langfuse_minio_data:");
    }

    @Test
    void migrationAssetsPreserveSourceAndVerifyDownloadedContent() throws IOException {
        String migrationCompose = Files.readString(Path.of("compose.langfuse-migration.yaml"));
        String migrationScript = Files.readString(Path.of("scripts/migrate-langfuse-minio-to-garage.sh"));

        assertThat(migrationCompose)
            .contains("langfuse_minio_source:")
            .contains("external: true")
            .contains("name: '${LANGFUSE_MINIO_VOLUME_NAME:-graphrag_langfuse_minio_data}'")
            .contains("image: 'rclone/rclone:1.74.4'");
        assertThat(migrationScript)
            .contains("run_rclone copy minio:langfuse garage:langfuse")
            .contains("run_rclone check minio:langfuse garage:langfuse")
            .contains("--download --one-way")
            .doesNotContain("rclone sync", "rclone move", "purge");
    }
}
