## ADDED Requirements

### Requirement: Processing stage transitions remain durable under orchestration extraction
The system SHALL preserve processing-run stage, status, completion, error, and active-completed semantics while document processing is decomposed into workflow stages.

#### Scenario: Stage-oriented processing succeeds
- **WHEN** all document processing stages complete successfully
- **THEN** the processing run is completed and active according to the existing processing-run contract

#### Scenario: Stage-oriented processing fails
- **WHEN** one processing stage fails after a run starts
- **THEN** the processing run is failed with its stage and error recorded
- **AND** a previously active completed processing run remains active when applicable
