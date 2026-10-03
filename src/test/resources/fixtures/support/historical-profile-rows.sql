-- Baseline f4733eb V2/V7 column and stored-tokenizer contracts; no Java class discriminator.
INSERT INTO app.ai_profile
    (id, name, base_url, api_key, chat_model, embedding_model, tokenizer_id,
     embedding_dimensions, timeout_seconds, max_retries, default_profile,
     revision, created_at, updated_at, version)
VALUES
    ('historical-implicit', 'Historical implicit', 'https://provider.example/v1', 'fixture-secret',
     'chat-model', 'text-embedding-3-small', NULL, 1536, 60, 2, false, 7,
     '2026-07-29T10:15:30Z', '2026-07-30T11:16:31Z', 0),
    ('historical-explicit', 'Historical explicit', 'https://provider.example/v1', NULL,
     'chat-model', 'custom-model', 'cl100k_base', 768, 60, 2, false, 7,
     '2026-07-29T10:15:30Z', '2026-07-30T11:16:31Z', 0),
    ('historical-conservative', 'Historical conservative', 'https://provider.example/v1', NULL,
     'chat-model', 'custom-model', 'utf8-byte-v1', 768, 60, 2, false, 7,
     '2026-07-29T10:15:30Z', '2026-07-30T11:16:31Z', 0);
