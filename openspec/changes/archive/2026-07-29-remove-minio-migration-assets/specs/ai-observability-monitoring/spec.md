## MODIFIED Requirements

### Requirement: Optional local Langfuse uses Garage object storage
The system SHALL provide Garage as the sole S3-compatible object-storage service described and configured for the optional local Langfuse Compose stack.

#### Scenario: Default Compose startup
- **WHEN** the Langfuse Compose profile is not selected
- **THEN** Garage and the other profile-gated Langfuse services are not started

#### Scenario: Langfuse profile startup
- **WHEN** the Langfuse Compose profile is selected
- **THEN** a pinned Garage image starts with persistent metadata and object-data volumes
- **AND** an idempotent initialization step configures the local cluster layout, `langfuse` bucket, and scoped access key before Langfuse web and worker become ready
- **AND** all documented and tested local object-storage paths use Garage

#### Scenario: Garage restarts
- **WHEN** the Garage container is recreated with its persistent volumes intact
- **THEN** the initialized cluster layout, bucket configuration, credentials, and stored Langfuse objects remain available

## REMOVED Requirements

### Requirement: Existing Langfuse objects can be migrated without destructive cutover
**Reason**: The one-time transition to Garage is complete and the repository no longer supports the former migration or rollback path as an active operational capability.

**Migration**: Use the Garage-backed Langfuse profile and Garage smoke test. Historical transition details remain available in the archived `replace-langfuse-minio-with-garage` OpenSpec change.
