## 1. Prompt Contract Hardening

- [x] 1.1 Update schema generation prompt template/instructions to require each node `key` to match one declared node property name.
- [x] 1.2 Add prompt guidance to avoid generic `id` unless `id` is explicitly present in that node's properties.
- [x] 1.3 Add/adjust prompt-focused tests or assertions proving the key/property contract text is present.

## 2. Advisory Post-Generation Validator

- [x] 2.1 Implement a deterministic advisory check over generated schema nodes for key/property mismatches.
- [x] 2.2 Emit one warning per mismatched node including node index/label and missing key name.
- [x] 2.3 Emit actionable suggestions per mismatch (e.g., add missing property or change key).
- [x] 2.4 Ensure advisory warnings do not block returning generated schema payload.

## 3. Response And Controller Integration

- [x] 3.1 Extend schema generation response contract with additive warnings/suggestions fields.
- [x] 3.2 Wire advisory output through service/controller response mapping.
- [x] 3.3 Preserve backward compatibility for clients that ignore new fields.
- [x] 3.4 Use structured warning objects (node index, node label, warning code, message, suggestions), not plain string warnings.
- [x] 3.5 Do not add a `severity` field yet; defer it until additional advisory categories exist and document this in response contract notes.

## 4. Tests And Verification

- [x] 4.1 Add unit tests for advisory mismatch detection with single and multiple node mismatches.
- [x] 4.2 Add controller/service tests verifying warnings appear in generation response while response remains successful.
- [x] 4.3 Run targeted schema generation tests.
- [x] 4.4 Run `./mvnw test`.
- [x] 4.5 Run `openspec validate strengthen-schema-generation-key-property-guidance --strict`.
