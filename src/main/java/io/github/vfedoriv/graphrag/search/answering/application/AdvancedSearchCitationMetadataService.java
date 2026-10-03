package io.github.vfedoriv.graphrag.search.answering.application;
import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts;

import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchCitationCatalog;

import io.github.vfedoriv.graphrag.search.answering.domain.AdvancedSearchAnswerContracts.Evidence;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchDocumentMetadata.Metadata;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchDocumentMetadata;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Adds immutable, citation-safe document metadata to a newly assembled result. */
@Component
public class AdvancedSearchCitationMetadataService {
    private static final int MAX_DOCUMENT_LOOKUP = 128;

    private final SearchDocumentMetadata documentUploadRepository;

    public AdvancedSearchCitationMetadataService(SearchDocumentMetadata documentUploadRepository) {
        this.documentUploadRepository = documentUploadRepository;
    }

    public AdvancedSearchCitationCatalog.Catalog enrich(
        String knowledgeBaseId,
        AdvancedSearchCitationCatalog.Catalog catalog
    ) {
        Set<String> identifiers = new LinkedHashSet<>();
        catalog.evidence().forEach(value -> identifiers.add(value.documentId()));
        catalog.contexts().forEach(value -> identifiers.add(value.documentId()));
        List<String> boundedIdentifiers = identifiers.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_DOCUMENT_LOOKUP)
            .toList();
        Map<String, Metadata> documents = new LinkedHashMap<>();
        documentUploadRepository.findOwnedBatch(knowledgeBaseId, boundedIdentifiers)
            .forEach(value -> documents.put(value.documentId(), value));

        List<String> warnings = new java.util.ArrayList<>();
        List<Evidence> evidence = catalog.evidence().stream()
            .map(value -> enrich(value, documents, warnings))
            .toList();
        List<Evidence> contexts = catalog.contexts().stream()
            .map(value -> enrich(value, documents, warnings))
            .toList();
        return catalog.withEvidence(evidence, contexts, warnings);
    }

    private Evidence enrich(Evidence value, Map<String, Metadata> documents, List<String> warnings) {
        Metadata document = documents.get(value.documentId());
        String filename = document == null ? fallback(value.documentId()) : safe(document.filename());
        String contentType = document == null ? null : safe(document.contentType());
        String displayLabel = filename;
        if (document == null) {
            warnings.add("SOURCE_METADATA_UNAVAILABLE");
        }
        return new Evidence(
            value.citationId(), value.type(), value.chunkId(), value.documentId(), value.range(),
            value.processingRunId(), value.effectiveChunkerRevision(), value.structuralPath(), value.text(),
            value.rank(), value.score(), filename, contentType, displayLabel
        );
    }

    private String fallback(String documentId) {
        return documentId == null || documentId.isBlank() ? "unknown-document" : "document-" + documentId;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
