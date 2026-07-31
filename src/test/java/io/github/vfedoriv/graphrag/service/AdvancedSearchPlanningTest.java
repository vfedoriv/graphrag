package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchGraphRetrievalContracts.ComparisonOperator;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.CoverageStatus;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.GraphFilter;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.GraphProjection;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.GraphRequest;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.LiteralType;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.LiteralValue;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.MetadataConstraint;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.Plan;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.Refinement;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchPlanningContracts.Subquestion;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceSource;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.SourceBounds;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.schema.SchemaDocument;
import io.github.vfedoriv.graphrag.service.AdvancedSearchFollowUpPolicy.Decision;
import io.github.vfedoriv.graphrag.service.AdvancedSearchPlanValidator.ValidatedPlan;
import io.github.vfedoriv.graphrag.service.AdvancedSearchSufficiencyEvaluator.Outcome;
import io.github.vfedoriv.graphrag.service.GraphPlanValidationService.ValidationResult;
import io.github.vfedoriv.graphrag.service.RuntimeSettingsService.AdvancedSearchSettings;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class AdvancedSearchPlanningTest {

    private final GraphPlanValidationService graphValidation = mock(GraphPlanValidationService.class);
    private final AdvancedSearchPlanValidator validator = new AdvancedSearchPlanValidator(graphValidation);
    private final AdvancedSearchSettings settings = settings();

    @Test
    void plannerParsesBoundedMultiPartPlan() {
        ProfileScopedAiClientResolver resolver = resolver("""
            {
              "version":1,
              "subquestions":[
                {"id":"q-1","text":"Who approved the project?"},
                {"id":"q-2","text":"When was it approved?"}
              ],
              "exactTerms":["Project Atlas"],
              "metadata":{"filename":null,"contentType":"application/pdf"},
              "graphRequests":[]
            }
            """);
        AdvancedSearchPlanner planner = new AdvancedSearchPlanner(resolver, validator);

        ValidatedPlan plan = planner.plan(
            "Who approved Project Atlas and when?", schemaContext(), settings, Instant.now().plusSeconds(30)
        );

        assertThat(plan.subqueries()).extracting(value -> value.id()).containsExactly("q-1", "q-2");
        assertThat(plan.exactTerms()).containsExactly("Project Atlas");
        assertThat(plan.metadata().contentType()).isEqualTo("application/pdf");
        assertThat(plan.summary().fallbackUsed()).isFalse();
    }

    @Test
    void malformedOrExecutablePlannerOutputUsesDeterministicOriginalQueryFallback() {
        AdvancedSearchPlanner malformed = new AdvancedSearchPlanner(resolver("not-json"), validator);
        AdvancedSearchPlanner executable = new AdvancedSearchPlanner(
            resolver("{\"cypher\":\"MATCH (n) RETURN n\"}"), validator
        );

        ValidatedPlan malformedPlan = malformed.plan(
            "  original   bounded query  ", schemaContext(), settings, Instant.now().plusSeconds(30)
        );
        ValidatedPlan executablePlan = executable.plan(
            "original bounded query", schemaContext(), settings, Instant.now().plusSeconds(30)
        );

        assertThat(malformedPlan.subqueries().getFirst().text()).isEqualTo("original bounded query");
        assertThat(malformedPlan.summary().fallbackUsed()).isTrue();
        assertThat(executablePlan.summary().fallbackUsed()).isTrue();
        assertThat(executablePlan.graphPlans()).isEmpty();
    }

    @Test
    void planBoundsAndStaleSchemaPropertiesAreRejectedBeforeRetrieval() {
        Plan tooMany = new Plan(1, List.of(
            new Subquestion("1", "one"), new Subquestion("2", "two"),
            new Subquestion("3", "three"), new Subquestion("4", "four")
        ), List.of(), null, List.of());
        when(graphValidation.validate(any(), any(), anyInt(), any()))
            .thenReturn(new ValidationResult(false, List.of("filter.property-unknown"), null));
        GraphRequest stale = new GraphRequest(
            "g-1", "Person",
            List.of(new GraphFilter(0, "removedProperty", ComparisonOperator.EQUALS,
                new LiteralValue(LiteralType.STRING, "value", List.of()))),
            List.of(),
            List.of(new GraphProjection(0, "name", "name")),
            5
        );
        Plan stalePlan = new Plan(
            1, List.of(new Subquestion("q-1", "find person")), List.of(),
            new MetadataConstraint(null, null), List.of(stale)
        );

        assertThatThrownBy(() -> validator.validate(tooMany, schemaContext(), settings))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("plan.subquestions.limit");
        assertThatThrownBy(() -> validator.validate(stalePlan, schemaContext(), settings))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("filter.property-unknown");
    }

    @Test
    void evaluatorReportsGroundedContradictionsAndCompleteCoverage() {
        ProfileScopedAiClientResolver resolver = resolver("""
            {
              "version":1,
              "coverage":[
                {"subquestionId":"q-1","status":"COMPLETE","evidenceIds":["c-1"]},
                {"subquestionId":"q-2","status":"COMPLETE","evidenceIds":["c-2"]}
              ],
              "contradictions":[{"category":"DATE_CONFLICT","evidenceIds":["c-1","c-2"]}],
              "gap":{"concrete":false,"category":null},
              "refinements":[]
            }
            """);
        AdvancedSearchSufficiencyEvaluator evaluator = new AdvancedSearchSufficiencyEvaluator(resolver);
        ValidatedPlan plan = validator.validate(new Plan(
            1,
            List.of(new Subquestion("q-1", "who"), new Subquestion("q-2", "when")),
            List.of(), null, List.of()
        ), schemaContext(), settings);

        Outcome outcome = evaluator.evaluate(
            "who and when", plan, List.of(evidence("c-1"), evidence("c-2")),
            settings, Instant.now().plusSeconds(30)
        );

        assertThat(outcome.result().coverage()).extracting(value -> value.status())
            .containsOnly(CoverageStatus.COMPLETE);
        assertThat(outcome.result().contradictions()).hasSize(1);
        assertThat(outcome.summary().contradictionCount()).isEqualTo(1);
        assertThat(outcome.summary().concreteGap()).isFalse();
    }

    @Test
    void noEvidenceCreatesBoundedRefinementsWhileDeadlineAndCancellationGateExecution() {
        AdvancedSearchSufficiencyEvaluator evaluator = new AdvancedSearchSufficiencyEvaluator(resolver(null));
        ValidatedPlan plan = validator.validate(new Plan(
            1,
            List.of(new Subquestion("q-1", "first"), new Subquestion("q-2", "second"),
                new Subquestion("q-3", "third")),
            List.of(), null, List.of()
        ), schemaContext(), settings);
        Outcome outcome = evaluator.evaluate(
            "multi-part", plan, List.of(), settings, Instant.now().plusSeconds(30)
        );
        AdvancedSearchFollowUpPolicy policy = new AdvancedSearchFollowUpPolicy();

        Decision allowed = policy.decide(
            outcome.result(), Instant.now().plusSeconds(30), settings, () -> false
        );
        Decision exhausted = policy.decide(
            outcome.result(), Instant.now().plusSeconds(12), settings, () -> false
        );
        Decision cancelled = policy.decide(
            outcome.result(), Instant.now().plusSeconds(30), settings, () -> true
        );

        assertThat(outcome.result().refinements()).hasSize(2);
        assertThat(allowed.execute()).isTrue();
        assertThat(allowed.refinements()).extracting(Refinement::query).containsExactly("first", "second");
        assertThat(exhausted.execute()).isFalse();
        assertThat(exhausted.skippedCategory()).isEqualTo("DEADLINE_RESERVE");
        assertThat(cancelled.execute()).isFalse();
        assertThat(cancelled.skippedCategory()).isEqualTo("CANCELLED");
    }

    @Test
    void malformedSufficiencyOutputDoesNotStartSpeculativeFollowUp() {
        AdvancedSearchSufficiencyEvaluator evaluator = new AdvancedSearchSufficiencyEvaluator(resolver("not-json"));
        ValidatedPlan plan = validator.validate(new Plan(
            1, List.of(new Subquestion("q-1", "question")), List.of(), null, List.of()
        ), schemaContext(), settings);

        Outcome outcome = evaluator.evaluate(
            "question", plan, List.of(evidence("c-1")), settings, Instant.now().plusSeconds(30)
        );
        Decision decision = new AdvancedSearchFollowUpPolicy().decide(
            outcome.result(), Instant.now().plusSeconds(30), settings, () -> false
        );

        assertThat(outcome.summary().fallbackUsed()).isTrue();
        assertThat(decision.execute()).isFalse();
        assertThat(decision.skippedCategory()).isEqualTo("NO_CONCRETE_GAP");
    }

    private ProfileScopedAiClientResolver resolver(String response) {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        when(resolver.chatModel()).thenReturn(response == null ? null : chatModel(response));
        return resolver;
    }

    private ChatModel chatModel(String response) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
            }
        };
    }

    private ActiveSchemaContext schemaContext() {
        SchemaDocument schema = new SchemaDocument(
            "test", 1, null,
            List.of(new SchemaDocument.NodeDefinition(
                "Person", null, List.of("name"),
                List.of(new SchemaDocument.PropertyDefinition("name", "string", true))
            )),
            List.of(), List.of(), List.of()
        );
        return new ActiveSchemaContext("kb-1", "schema-1", null, schema);
    }

    private EvidenceCandidate evidence(String id) {
        return new EvidenceCandidate(
            new EvidenceSource(id, "doc-" + id, 0, new SourceBounds(0, 10, 1, 1),
                "run-1", "revision-1", "section"),
            CitationKind.TEXT_CHILD,
            "evidence text " + id,
            List.of(), List.of(), null, 1.0, 1, null
        );
    }

    private AdvancedSearchSettings settings() {
        return new AdvancedSearchSettings(
            Duration.ofSeconds(60), 10, 20, 60, 200, 20, 10, 20, 4000, 8000,
            3, 8, 2, 1000, 10, 1200, 2,
            Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofHours(24), 100
        );
    }
}
