package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictType;
import io.github.vfedoriv.graphrag.repository.SchemaDraftConflictRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaDraftConflictServiceTest {
    @Mock private SchemaDraftConflictRepository conflictRepository;
    private SchemaDraftJsonSupport jsonSupport;
    private SchemaDraftConflictService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        jsonSupport = new SchemaDraftJsonSupport(objectMapper);
        service = new SchemaDraftConflictService(conflictRepository, jsonSupport, objectMapper);
    }

    @Test
    void semanticKeyIgnoresAlternativeOrderObjectFieldOrderAndDuplicates() {
        String first = service.semanticKey(SchemaDraftConflictType.TYPE, "node-property:Person:age", List.of(
            Map.of("type", "STRING", "required", false),
            Map.of("type", "INTEGER", "required", true),
            Map.of("required", false, "type", "STRING")
        ));
        String reordered = service.semanticKey(SchemaDraftConflictType.TYPE, "node-property:Person:age", List.of(
            Map.of("required", true, "type", "INTEGER"),
            Map.of("required", false, "type", "STRING")
        ));
        String changed = service.semanticKey(SchemaDraftConflictType.TYPE, "node-property:Person:age", List.of(
            Map.of("required", true, "type", "LONG"),
            Map.of("required", false, "type", "STRING")
        ));

        assertThat(first).isEqualTo(reordered).isNotEqualTo(changed);
        assertThat(service.semanticKey(SchemaDraftConflictType.KEY, "node-property:Person:age", List.of(
            Map.of("required", true, "type", "INTEGER"),
            Map.of("required", false, "type", "STRING")
        ))).isNotEqualTo(first);
    }

    @Test
    void carriesSelectedResolutionToAnIndependentEquivalentConflict() {
        preparePersistence();
        Instant historicalResolvedAt = Instant.parse("2026-07-01T10:00:00Z");
        SchemaDraftConflictNode historical = historical(
            SchemaDraftConflictType.TYPE, List.of("STRING", "INTEGER"));
        historical.setSelectedAlternative("INTEGER");
        historical.setResolvedAt(historicalResolvedAt);
        when(conflictRepository.findResolvedHistory("draft", "aggregate-2")).thenReturn(List.of(historical));

        SchemaDraftConflictNode current = service.create(
            "draft", "aggregate-2", SchemaDraftConflictType.TYPE, "node-property:Person:age",
            List.of("INTEGER", "STRING"), List.of(Map.of("sourceId", "new-source")));

        assertThat(current.getId()).isNotEqualTo(historical.getId());
        assertThat(current.getAggregateRevisionId()).isEqualTo("aggregate-2");
        assertThat(current.isResolved()).isTrue();
        assertThat(current.getSelectedAlternative()).isEqualTo("INTEGER");
        assertThat(current.getResolvedAt()).isAfter(historicalResolvedAt);
        assertThat(historical.getAggregateRevisionId()).isEqualTo("aggregate-1");
        assertThat(historical.getResolvedAt()).isEqualTo(historicalResolvedAt);
    }

    @Test
    void carriesValidCustomResolutionButRejectsIncompleteLatestHistory() {
        preparePersistence();
        SchemaDraftConflictNode valid = historical(
            SchemaDraftConflictType.KEY, List.of("personId", "email"));
        valid.setCustomResolutionJson(jsonSupport.canonical(Map.of("keys", List.of("personId", "email"))));
        when(conflictRepository.findResolvedHistory("draft", "aggregate-2")).thenReturn(List.of(valid));

        SchemaDraftConflictNode carried = service.create(
            "draft", "aggregate-2", SchemaDraftConflictType.KEY, "node-key:Person",
            List.of("email", "personId"), List.of());

        assertThat(carried.isResolved()).isTrue();
        assertThat(jsonSupport.parse(carried.getCustomResolutionJson()).path("keys").toString())
            .isEqualTo("[\"personId\",\"email\"]");

        SchemaDraftConflictNode incomplete = historical(
            SchemaDraftConflictType.KEY, List.of("personId", "email"));
        incomplete.setCustomResolutionJson("{}");
        when(conflictRepository.findResolvedHistory("draft", "aggregate-3"))
            .thenReturn(List.of(incomplete, valid));

        SchemaDraftConflictNode unresolved = service.create(
            "draft", "aggregate-3", SchemaDraftConflictType.KEY, "node-key:Person",
            List.of("email", "personId"), List.of());

        assertThat(unresolved.isResolved()).isFalse();
        assertThat(unresolved.getCustomResolutionJson()).isNull();
    }

    @Test
    void leavesChangedOrInvalidRecurringConflictsUnresolved() {
        preparePersistence();
        SchemaDraftConflictNode historical = historical(
            SchemaDraftConflictType.TYPE, List.of("STRING", "INTEGER"));
        historical.setSelectedAlternative("INTEGER");
        when(conflictRepository.findResolvedHistory("draft", "aggregate-2")).thenReturn(List.of(historical));
        when(conflictRepository.findResolvedHistory("draft", "aggregate-3")).thenReturn(List.of(historical));

        SchemaDraftConflictNode changedAlternatives = service.create(
            "draft", "aggregate-2", SchemaDraftConflictType.TYPE, "node-property:Person:age",
            List.of("STRING", "LONG"), List.of());
        SchemaDraftConflictNode changedType = service.create(
            "draft", "aggregate-3", SchemaDraftConflictType.KEY, "node-property:Person:age",
            List.of("STRING", "INTEGER"), List.of());

        assertThat(changedAlternatives.isResolved()).isFalse();
        assertThat(changedType.isResolved()).isFalse();
    }

    @Test
    void revalidatesSelectedAlternativeEvenWhenStoredSemanticKeyMatches() {
        preparePersistence();
        SchemaDraftConflictNode inconsistent = historical(
            SchemaDraftConflictType.TYPE, List.of("STRING", "INTEGER"));
        inconsistent.setSelectedAlternative("BOOLEAN");
        inconsistent.setSemanticKey(service.semanticKey(
            SchemaDraftConflictType.TYPE, "node-property:Person:age", List.of("STRING", "INTEGER")));
        when(conflictRepository.findResolvedHistory("draft", "aggregate-2")).thenReturn(List.of(inconsistent));

        SchemaDraftConflictNode current = service.create(
            "draft", "aggregate-2", SchemaDraftConflictType.TYPE, "node-property:Person:age",
            List.of("INTEGER", "STRING"), List.of());

        assertThat(current.isResolved()).isFalse();
        assertThat(current.getSelectedAlternative()).isNull();
    }

    private SchemaDraftConflictNode historical(
        SchemaDraftConflictType type, List<String> alternatives
    ) {
        SchemaDraftConflictNode conflict = new SchemaDraftConflictNode();
        conflict.setId("historical-" + type.name());
        conflict.setDraftId("draft");
        conflict.setAggregateRevisionId("aggregate-1");
        conflict.setType(type);
        conflict.setCoordinate(type == SchemaDraftConflictType.KEY
            ? "node-key:Person" : "node-property:Person:age");
        conflict.setAlternativesJson(jsonSupport.canonical(alternatives));
        conflict.setEvidenceJson("[]");
        conflict.setResolved(true);
        conflict.setCreatedAt(Instant.parse("2026-07-01T09:00:00Z"));
        return conflict;
    }

    private void preparePersistence() {
        when(conflictRepository.save(any(SchemaDraftConflictNode.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
