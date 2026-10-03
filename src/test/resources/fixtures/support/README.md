# Support relocation anchors

`historical-api-record-fields.json` captures every record component list (including
nested records) from the 29 moved legacy API/value classes at baseline
`f4733ebb65208755449cd9ba4340e455e72c8218`. Class names identify final owners;
component names/order come from `git show` of baseline source, not the relocated
implementation. JSON key omission/value behavior remains covered by controller,
OpenAPI, and dedicated historical AI JSON fixtures.

The chunk revision constants in `DocumentChunkRevisionsCompatibilityTest` were
calculated with the unchanged baseline `ChunkRevisionCalculator`, original
strategy/tokenizer/parser revision strings, and baseline settings hash inputs.

`historical-profile-rows.sql` captures baseline profile column/value contracts,
including null, explicit, and legacy stored conservative tokenizer strings.
The relational fixture reads and saves those rows and compares persisted values;
normal optimistic version increments are the only excluded bookkeeping field.
