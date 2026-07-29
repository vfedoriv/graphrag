## ADDED Requirements

### Requirement: Optional local Langfuse uses Garage object storage
The system SHALL provide Garage as the S3-compatible object-storage service for the optional local Langfuse Compose stack and SHALL NOT require MinIO for normal Langfuse profile startup.

#### Scenario: Default Compose startup
- **WHEN** the Langfuse Compose profile is not selected
- **THEN** Garage and the other profile-gated Langfuse services are not started

#### Scenario: Langfuse profile startup
- **WHEN** the Langfuse Compose profile is selected
- **THEN** a pinned Garage image starts with persistent metadata and object-data volumes
- **AND** an idempotent initialization step configures the local cluster layout, `langfuse` bucket, and scoped access key before Langfuse web and worker become ready
- **AND** the normal profile does not start or depend on a MinIO service

#### Scenario: Garage restarts
- **WHEN** the Garage container is recreated with its persistent volumes intact
- **THEN** the initialized cluster layout, bucket configuration, credentials, and stored Langfuse objects remain available

### Requirement: Langfuse uses Garage-compatible S3 endpoints
The system SHALL configure Langfuse web and worker with the Garage region, credentials, endpoint style, and endpoint reachability required for event and media object operations.

#### Scenario: Langfuse stores event objects
- **WHEN** Langfuse web or worker writes or reads event-upload objects
- **THEN** it uses the Garage endpoint reachable inside the Compose network
- **AND** it uses the configured Garage region and path-style S3 access

#### Scenario: Browser accesses media
- **WHEN** Langfuse issues a presigned media upload or download URL to a browser or SDK client
- **THEN** the signed URL uses the configured host-reachable Garage media endpoint
- **AND** the client can complete the media operation without resolving a Compose-only service hostname

#### Scenario: Garage is unavailable
- **WHEN** Garage is unhealthy or its Langfuse bucket initialization has not completed
- **THEN** Langfuse web and worker are not reported as ready against an unusable object-store dependency

### Requirement: Existing Langfuse objects can be migrated without destructive cutover
The system MUST provide a repeatable migration procedure that copies existing Langfuse objects from MinIO to Garage, verifies the copy, supports rollback, and preserves the MinIO data volume until an explicit retirement step.

#### Scenario: Operator prepares migration
- **WHEN** an operator follows the migration procedure before switching Langfuse endpoints
- **THEN** MinIO and Garage can be made available concurrently
- **AND** the procedure copies the existing `langfuse` bucket without deleting source objects

#### Scenario: Operator verifies copied objects
- **WHEN** the initial and final delta copies complete
- **THEN** the procedure verifies source and destination object inventories and content integrity
- **AND** it does not assume that multipart-object ETags are content hashes

#### Scenario: Operator cuts over Langfuse
- **WHEN** copied objects have been verified and Langfuse writes are quiesced
- **THEN** a final delta copy runs before Langfuse web and worker are configured to use Garage
- **AND** event ingestion plus media upload and download are smoke-tested after startup

#### Scenario: Operator rolls back
- **WHEN** Garage-backed Langfuse fails acceptance checks during the rollback window
- **THEN** the operator can restore the prior MinIO endpoints and credentials
- **AND** the preserved MinIO volume remains available for rollback

### Requirement: Garage adoption is scoped to Langfuse
The system SHALL keep GraphRAG application binary storage behavior unchanged by the Langfuse object-store replacement.

#### Scenario: GraphRAG document is uploaded
- **WHEN** the Langfuse profile uses Garage and a GraphRAG document or schema-draft file is uploaded
- **THEN** the application continues to use its configured local filesystem binary-storage backend
- **AND** no GraphRAG document or draft-source locator is migrated to Garage by this change
