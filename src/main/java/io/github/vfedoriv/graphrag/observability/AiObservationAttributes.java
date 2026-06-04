package io.github.vfedoriv.graphrag.observability;

public final class AiObservationAttributes {

    public static final String OPERATION = "ai.operation";
    public static final String WORKFLOW = "ai.workflow";
    public static final String PROVIDER_PROFILE = "ai.provider.profile";
    public static final String MODEL_NAME = "ai.model.name";
    public static final String STATUS = "ai.status";
    public static final String FAILURE_CATEGORY = "ai.failure.category";
    public static final String CONTENT_CAPTURE = "ai.content_capture";
    public static final String SCHEMA_NAME = "ai.schema.name";

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";
    public static final String UNKNOWN = "unknown";

    private AiObservationAttributes() {
    }
}
