These fixtures freeze the pre-step-9 AI profile HTTP response fields declared by
`dto.AiProfileResponse` and produced by `service.AiProfileService.toResponse`
at the repository HEAD before `finalize-support-boundaries-assembly`.
They are hand-recorded expected outputs, not generated from the relocated code.
The implicit fixture covers null explicit tokenizer, automatic model resolution,
configured-key masking (`first four + ... + last four`), default selection and
ISO-8601 timestamps. The explicit fixture covers a custom embedding model,
explicit tokenizer, absent key and null mask. Keys are test inputs only and are
never present in the response fixtures.
