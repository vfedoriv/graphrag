package io.github.vfedoriv.graphrag.schemas.publication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaFormat;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaStatus;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.observability.AiObservationScope;
import io.github.vfedoriv.graphrag.observability.AiObservationService;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaRegistryCapabilities;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaValidationResult;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftAdmissions;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftPublicationLink;
import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftReviewInputs;
import io.github.vfedoriv.graphrag.schemas.evaluation.contracts.PublicationEvaluationQualifications;
import io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos.*;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationStatus;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class SchemaDraftPublicationServiceTest {
    private static final String PROJECTION = "{\"version\":1,\"name\":\"target\",\"nodes\":[]}";
    private final DraftAdmissions admissions = mock(DraftAdmissions.class);
    private final DraftReviewInputs reviews = mock(DraftReviewInputs.class);
    private final SchemaDraftPublicationRepository publications = mock(SchemaDraftPublicationRepository.class);
    private final SchemaRegistryCapabilities registry = mock(SchemaRegistryCapabilities.class);
    private final PublicationJsonSupport json = new PublicationJsonSupport(new ObjectMapper());
    private final PublicationEvaluationQualifications evaluations = mock(PublicationEvaluationQualifications.class);
    private final AiObservationService observations = mock(AiObservationService.class);
    private final PublicationCheckpointService checkpoints = mock(PublicationCheckpointService.class);
    private final SchemaDraftPublicationService service = new SchemaDraftPublicationService(
        admissions, reviews, publications, registry, json, evaluations, observations, checkpoints);
    private final DraftAdmissions.Draft draft = new DraftAdmissions.Draft(
        "draft", "kb", 7, 3L, "target", 1, "aggregate", "{}", true, null, null);
    private final String canonical = json.canonical(json.parse(PROJECTION));
    private final String hash = json.fingerprint(canonical);

    @BeforeEach void setup() {
        when(admissions.requireOwned("kb", "draft")).thenReturn(draft);
        when(admissions.requireMutable("kb", "draft", 7)).thenReturn(draft);
        when(reviews.projection("kb", "draft")).thenReturn(new DraftReviewInputs.Projection("aggregate", 7, PROJECTION));
        when(reviews.aggregate("kb", "draft", "aggregate")).thenReturn(
            new DraftReviewInputs.Aggregate("[]", Set.of(), List.of()));
        when(registry.parseAndValidate(canonical)).thenReturn(new SchemaValidationResult(null, List.of()));
        when(evaluations.requirement()).thenReturn(new PublicationEvaluationQualifications.Requirement(false, 1));
        when(observations.startWorkflow(any())).thenReturn(mock(AiObservationScope.class));
    }

    @Test void readinessPreservesBlockingOrderAndGuidedOnlyDecisionRules() {
        when(registry.parseAndValidate(canonical)).thenReturn(new SchemaValidationResult(null, List.of("first", "second")));
        when(reviews.aggregate("kb", "draft", "aggregate")).thenReturn(new DraftReviewInputs.Aggregate("""
            [{"identity":"guided","origins":["GUIDED"]},
             {"identity":"observed","origins":["GUIDED","OBSERVED"]},
             {"identity":"decided","origins":["GUIDED"]}]
            """, Set.of("decided"), List.of(
                new DraftReviewInputs.Conflict("type", "TYPE", "node:type", false),
                new DraftReviewInputs.Conflict("resolved", "KEY", "node:key", true),
                new DraftReviewInputs.Conflict("nonblocking", "DESCRIPTION", "node:description", false))));
        when(registry.identityExistsGlobally("target", 1)).thenReturn(true);
        when(evaluations.requirement()).thenReturn(new PublicationEvaluationQualifications.Requirement(true, 2));
        PublicationReadinessResponse response = service.readiness("kb", "draft");
        assertThat(response.ready()).isFalse();
        assertThat(response.blockingReasons()).extracting(ReadinessBlockingReason::id)
            .containsExactly("registry-validation:0", "registry-validation:1", "type", "guided", "target-identity", "evaluation-current");
        assertThat(response.projectionContentHash()).isEqualTo(hash);
        assertThat(response.draftRevision()).isEqualTo(7);
        verify(evaluations).qualifying("draft", 7, hash);
    }

    @Test void qualifyingEvaluationThresholdUsesExactRevisionAndCanonicalProjectionHash() {
        when(evaluations.requirement()).thenReturn(new PublicationEvaluationQualifications.Requirement(true, 2));
        when(evaluations.qualifying("draft", 7, hash)).thenReturn(Optional.of(
            new PublicationEvaluationQualifications.Run("evaluation", 7, hash, 1)));
        assertThat(service.readiness("kb", "draft").blockingReasons())
            .extracting(ReadinessBlockingReason::category).containsExactly("EVALUATION_THRESHOLD_NOT_MET");
        when(evaluations.qualifying("draft", 7, hash)).thenReturn(Optional.of(
            new PublicationEvaluationQualifications.Run("evaluation", 7, hash, 2)));
        assertThat(service.readiness("kb", "draft").ready()).isTrue();
    }

    @Test void registryValidationFailureBecomesFirstReadinessReason() {
        when(registry.parseAndValidate(canonical)).thenThrow(new IllegalArgumentException("invalid"));
        assertThat(service.readiness("kb", "draft").blockingReasons()).containsExactly(
            new ReadinessBlockingReason("registry-validation:0", "REGISTRY_VALIDATION", "invalid"));
    }

    @Test void durableIntentPrecedesInactiveRegistrationAndImmutableCompletion() {
        when(checkpoints.savePublicationIntent(any())).thenAnswer(invocation -> {
            SchemaDraftPublicationNode pending = invocation.getArgument(0);
            assertThat(pending.getStatus()).isEqualTo(SchemaDraftPublicationStatus.PENDING);
            assertThat(pending.getSchemaId()).isNull();
            assertThat(pending.isRetryable()).isTrue();
            return pending;
        });
        when(checkpoints.completePublication(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registry.registerGeneratedInactive(canonical, "kb")).thenReturn(schema(hash, SchemaStatus.INACTIVE));
        when(registry.findStoredById("schema")).thenReturn(Optional.of(schema(hash, SchemaStatus.INACTIVE)));
        PublicationResponse response = service.publish("kb", "draft", new PublishDraftRequest(7, hash));
        InOrder order = inOrder(checkpoints, registry);
        ArgumentCaptor<SchemaDraftPublicationNode> intent = ArgumentCaptor.forClass(SchemaDraftPublicationNode.class);
        order.verify(checkpoints).savePublicationIntent(intent.capture());
        order.verify(registry).findAssociatedByIdentity("kb", "target", 1);
        order.verify(registry).registerGeneratedInactive(canonical, "kb");
        ArgumentCaptor<DraftPublicationLink.Completion> completion = ArgumentCaptor.forClass(DraftPublicationLink.Completion.class);
        order.verify(checkpoints).completePublication(any(), completion.capture());
        assertThat(completion.getValue()).isEqualTo(new DraftPublicationLink.Completion(
            "kb", "draft", 7, "aggregate", 3L, "schema", hash, intent.getValue().getCompletedAt()));
        assertThat(response.active()).isFalse();
        assertThat(response.contentDrifted()).isFalse();
    }

    @Test void interruptedCompletionResumesAssociatedIdentityWithoutAnotherRegistration() {
        SchemaDraftPublicationNode publication = publication(SchemaDraftPublicationStatus.PENDING);
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication));
        when(registry.findAssociatedByIdentity("kb", "target", 1)).thenReturn(Optional.of(schema(hash, SchemaStatus.INACTIVE)));
        when(checkpoints.completePublication(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registry.findStoredById("schema")).thenReturn(Optional.of(schema(hash, SchemaStatus.INACTIVE)));
        assertThat(service.publish("kb", "draft", new PublishDraftRequest(7, hash)).schemaId()).isEqualTo("schema");
        verify(registry, never()).registerGeneratedInactive(anyString(), anyString());
        verify(checkpoints, never()).savePublicationIntent(any());
        assertThat(publication.getStatus()).isEqualTo(SchemaDraftPublicationStatus.COMPLETED);
        assertThat(publication.isRetryable()).isFalse();
    }

    @Test void interruptedCompletionRejectsDifferentAssociatedContent() {
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.PENDING)));
        when(registry.findAssociatedByIdentity("kb", "target", 1)).thenReturn(Optional.of(schema("different", SchemaStatus.INACTIVE)));
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(7, hash)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("content does not match");
        verifyNoInteractions(checkpoints);
        verify(registry, never()).registerGeneratedInactive(anyString(), anyString());
    }

    @Test void interruptedCompletionRejectsChangedAggregateEvenWithSameProjectionHash() {
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.PENDING)));
        when(reviews.projection("kb", "draft")).thenReturn(new DraftReviewInputs.Projection("another", 7, PROJECTION));
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(7, hash)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("exact aggregate revision");
        verifyNoInteractions(registry, checkpoints);
    }

    @Test void initialPublicationRejectsStaleHashAndClaimedTargetBeforeIntent() {
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(7, "stale")))
            .isInstanceOf(ConflictException.class).hasMessageContaining("Stale publication projection");
        when(publications.findByTargetIdentity("target:1")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.PENDING)));
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(7, hash)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("already claimed");
        verifyNoInteractions(checkpoints);
        verify(registry, never()).registerGeneratedInactive(anyString(), anyString());
    }

    @Test void repeatPublicationRequiresExactOriginalRevisionAndHash() {
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.COMPLETED)));
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(6, hash)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("preconditions");
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(7, "stale")))
            .isInstanceOf(ConflictException.class).hasMessageContaining("preconditions");
        verifyNoInteractions(checkpoints, registry);
    }

    @Test void identicalCompletedRetryReturnsStoredPublicationWithoutRegistrationOrCompletion() {
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.COMPLETED)));
        when(registry.findStoredById("schema")).thenReturn(Optional.of(schema(hash, SchemaStatus.INACTIVE)));
        PublicationResponse response = service.publish("kb", "draft", new PublishDraftRequest(7, hash));
        assertThat(response.publicationId()).isEqualTo("publication");
        assertThat(response.schemaId()).isEqualTo("schema");
        verifyNoInteractions(checkpoints);
        verify(registry, never()).registerGeneratedInactive(anyString(), anyString());
        verifyNoInteractions(reviews);
    }

    @Test void identityClaimedDuringRegistryRegistrationKeepsIntentPendingWithoutDraftCompletion() {
        ArgumentCaptor<SchemaDraftPublicationNode> intent = ArgumentCaptor.forClass(SchemaDraftPublicationNode.class);
        when(checkpoints.savePublicationIntent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registry.registerGeneratedInactive(canonical, "kb")).thenThrow(new ConflictException("identity occupied"));
        assertThatThrownBy(() -> service.publish("kb", "draft", new PublishDraftRequest(7, hash)))
            .isInstanceOf(ConflictException.class).hasMessage("identity occupied");
        verify(checkpoints).savePublicationIntent(intent.capture());
        assertThat(intent.getValue().getStatus()).isEqualTo(SchemaDraftPublicationStatus.PENDING);
        assertThat(intent.getValue().getSchemaId()).isNull();
        verify(checkpoints, never()).completePublication(any(), any());
    }

    @Test void retrievalReportsGlobalActiveStatusAndSubsequentContentDrift() {
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.COMPLETED)));
        when(registry.findStoredById("schema")).thenReturn(Optional.of(schema("changed", SchemaStatus.ACTIVE)));
        PublicationResponse response = service.get("kb", "draft");
        assertThat(response.active()).isTrue();
        assertThat(response.contentDrifted()).isTrue();
        assertThat(response.publicationContentHash()).isEqualTo(hash);
        assertThat(response.currentSchemaContentHash()).isEqualTo("changed");
        verify(admissions).requireOwned("kb", "draft");
    }

    @Test void retrievalRejectsMissingPublishedSchema() {
        when(publications.findByDraftId("draft")).thenReturn(Optional.of(publication(SchemaDraftPublicationStatus.COMPLETED)));
        assertThatThrownBy(() -> service.get("kb", "draft"))
            .isInstanceOf(NotFoundException.class).hasMessage("Published schema no longer exists: schema");
    }

    private SchemaDraftPublicationNode publication(SchemaDraftPublicationStatus status) {
        SchemaDraftPublicationNode result = new SchemaDraftPublicationNode();
        result.setId("publication"); result.setDraftId("draft"); result.setKnowledgeBaseId("kb");
        result.setSchemaId("schema"); result.setDraftRevision(7); result.setAggregateRevisionId("aggregate");
        result.setTargetIdentity("target:1"); result.setProjectionContentHash(hash); result.setStatus(status);
        result.setCreatedAt(Instant.parse("2026-01-02T03:04:05Z"));
        return result;
    }

    private SchemaSnapshot schema(String contentHash, SchemaStatus status) {
        return new SchemaSnapshot("kb", "schema", "target", 1, SchemaSourceType.GENERATED, SchemaFormat.JSON,
            status, canonical, contentHash, Instant.EPOCH, Instant.EPOCH, null);
    }
}
