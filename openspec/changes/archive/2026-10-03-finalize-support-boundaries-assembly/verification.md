# Final support and assembly verification

Date: 2026-10-03. Baseline: `f4733ebb65208755449cd9ba4340e455e72c8218`.
Implemented in the current checkout at the user's request; no commit or archive
was created. Pre-existing `.codebase-memory/` state is preserved.

## Executed checks

| Check | Observed result |
|---|---|
| Pre-migration `./mvnw test -Pfast` | 714 tests, zero failures/errors/skips |
| Final `./mvnw test -Pfast` | 740 tests, zero failures/errors/skips |
| Focused settings/profile relational selection | 7 tests, zero failures/errors/skips; PostgreSQL optimistic conflicts, masking, defaults and caller participation |
| Focused persistence/startup/query/search selection | 123 tests, zero failures/errors/skips; class list below |
| `./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest` | 1 test, zero failures/errors/skips |
| `./mvnw test` | 901 tests, zero failures/errors/skips, including 8 settings/profile relational cases with historical SQL read/save |
| `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest,ArchitectureBoundaryTest,FinalSupportBoundaryTest` | 76 tests, zero failures/errors/skips, after final guard cleanup |
| `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` | 6 tests, zero failures/errors/skips after section-anchor correction |
| `./mvnw site` | Success; 24 Markdown pages rendered, 598 local HTML links checked with no missing files/anchors |
| `openspec validate finalize-support-boundaries-assembly --strict` | Valid |
| `git diff --check` | Clean |

Container-backed commands and the portal build ran with escalated execution. The
first sandboxed portal attempt failed while updating Maven's read-only `~/.m2` cache;
the same escalated build succeeded without warnings. They provision temporary
test containers and do not delete operational volumes or migrate application data.

The focused integration selection was:

```text
KnowledgeBaseSchemaRelationalIntegrationTest
KnowledgeBaseControllerIntegrationTest
DocumentWorkflowRelationalRepositoryIntegrationTest
DocumentUploadIntegrationTest
DocumentProcessingIntegrationTest
SchemaWorkflowRelationalRepositoryIntegrationTest
SchemaDraftRelationalRepositoryIntegrationTest
SchemaDraftLifecycleIntegrationTest
SchemaRegistryIntegrationTest
SchemaDiscoveryIntegrationTest
PostgresProvisioningIntegrationTest
RelationalStartupIntegrationTest
PersistenceRoutingIntegrationTest
CypherValidationIntegrationTest
CypherExecutionIntegrationTest
AdvancedSearchTextRetrievalIntegrationTest
AdvancedSearchGraphRetrievalIntegrationTest
AdvancedSearchRankingExpansionIntegrationTest
AdvancedSearchRunIntegrationTest
```

## Review and requirements accounting

Independent final review inspected uncommitted tracked and new source/test files
against baseline source and change scenarios. It found two permanent-rule gaps,
reproduced by negative fixtures, then verified both fixes with no outstanding
finding. No confirmed production behavior regression was identified. The final
rules also remove an unused generic-observation provider-handle exemption and
replace stale identity-helper type names with final immutable AI facts.

[inventory.md](inventory.md) maps every one of the 179 historical frozen pairs
(76 document, 13 schema, 2 draft, 74 outward search, 14 inward search) to a final
capability/support/assembly role and enforcing rule, plus all 112 affected
legacy/helper classes. No roadmap-frozen set/resource/assertion remains.
Independent transaction constraints use exact call signatures and reject stale
or added pairs; they grant no cross-owner access.

[compatibility-audit.md](compatibility-audit.md) records original identities,
transaction semantics, final fixtures and source evidence. Existing HTTP/JSON/SQL,
snapshot/fingerprint/index/property/metric identities and checkpoint/recovery
contracts remain compatible. No SQL or binary migration is introduced. The
portal, contributor guidance and modularization roadmap describe the same final
ownership; no new portal page or navigation entry is needed.

The generated-site link audit found five pre-existing section-fragment mismatches
between Markdown links and Maven heading IDs. Explicit stable anchors in the
architecture page correct them; the final rendered link audit reports zero broken
local HTML links or section anchors.
