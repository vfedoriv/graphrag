# Implementation verification

Verified on 2026-10-01 in the current `dev` checkout. No HTTP, SQL migration,
snapshot-format, embedding-space hash, or cleanup algorithm changes.

| Check | Result |
|---|---|
| `./mvnw test -Pfast` | 552 tests passed |
| Final `./mvnw test -Pfast -Dtest=ArchitectureBoundaryTest` | 26 tests passed after final guard tightening |
| `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` | 6 tests passed |
| Focused container command below, outside sandbox | 39 tests passed |
| `./mvnw site` | Passed; 24 portal pages rendered |
| `openspec validate separate-knowledge-base-ai-state-dependencies --strict` | Passed |
| `git diff --check` | Passed |

Container verification command:

```bash
./mvnw test -Dtest='KnowledgeBaseControllerIntegrationTest,SettingsAndAiProfileRelationalIntegrationTest,GraphProvenanceIntegrationTest,DocumentControllerIntegrationTest,SchemaDraftLifecycleIntegrationTest#chunkPreparationParticipatesInCallerTransactionAndRecoveryUsesSavedTargets,EndToEndMvpFlowIntegrationTest'
```

Coverage includes compatible assignment commits; incompatible assignment/shared
profile rejection with unchanged fields, revisions, defaults, and associations;
stale assignment/profile versions; assigned-profile deletion rejection; count and
assignment reads seeing uncommitted caller state and rollback; owned deletion
admission preserving binaries/records/artifacts; empty deletion cleaning graph
artifacts and relational schema associations; other-KB/shared-fact preservation;
cleanup failure preventing relational deletion; historical preparation/recovery
targets; and document processing/query flow. Deterministic tests also check
rejected profile updates do not invalidate model clients.

The failure fixture independently commits graph cleanup before throwing. The
relational KB and schema association remain; the committed external deletion
remains effective. This verifies admission ordering and existing partial-effect
semantics, not distributed rollback. No new transactions were added to mapping
adapters or synchronous provider read facades.

The baseline had one failure among 533 tests: a stale roadmap link to the archived
migration-preparation change. Corrected as part of the planned documentation task.
New state-access architecture guards failed before migration. Additional negative
fixtures failed for relational-client and compatibility-rule adapter bypasses
before their guards were tightened.

Independent review found AI assignment-access, adapter relational-client, and
bridge dependency guard gaps. These are addressed with shared production/fixture
predicates, exact boundary value/client restrictions, and frozen bridge caller and
dependency sets. Unused duplicate URL normalization was removed. The legacy SHA
helper remains required by lexical/vector index identity callers; compatibility
identity derivation delegates to AI ownership.

Remaining transitional policy/identity/value callers and the immutable
`TokenizerId` allowance are named in the architecture portal, with retirement in
roadmap steps 4/8/9. Document consolidation and registry/discovery steps 4/5 remain
pending. This change is implemented, synced to main specs, and archived on 2026-10-01.
