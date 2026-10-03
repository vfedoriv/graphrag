package io.github.vfedoriv.graphrag.schemas.discovery.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Metadata for review-only multi-source schema discovery.")
public record SchemaDiscoveryRequest(
    @Schema(description = "Existing document identifiers owned by the knowledge base.")
    @Size(max = 50) List<@NotBlank String> documentIds,
    @Schema(description = "Independently analyzed pasted-text sources.")
    @Size(max = 50) List<@Valid TextSource> textSources,
    @Schema(description = "Optional unstructured domain guidance.")
    @Size(max = 20_000) String additionalInstructions,
    @Valid DiscoveryGuidance guidance
) {
    public SchemaDiscoveryRequest {
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
        textSources = textSources == null ? List.of() : List.copyOf(textSources);
        guidance = guidance == null ? DiscoveryGuidance.empty() : guidance;
    }

    public record TextSource(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String text
    ) {
    }

    public record DiscoveryGuidance(
        @Size(max = 10_000) String domainDescription,
        @Size(max = 50) List<@NotBlank @Size(max = 500) String> intendedQuestions,
        @Size(max = 100) List<@Valid ConceptRule> requiredConcepts,
        @Size(max = 100) List<@Valid ConceptRule> preferredConcepts,
        @Size(max = 100) List<@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ ]*") String> excludedConcepts,
        @Valid NamingRules namingRules,
        @Size(max = 100) List<@Valid PropertyRule> propertyRules,
        @Size(max = 100) List<@Valid RelationshipRule> relationshipRules
    ) {
        public DiscoveryGuidance {
            intendedQuestions = copy(intendedQuestions);
            requiredConcepts = copy(requiredConcepts);
            preferredConcepts = copy(preferredConcepts);
            excludedConcepts = copy(excludedConcepts);
            propertyRules = copy(propertyRules);
            relationshipRules = copy(relationshipRules);
        }

        public static DiscoveryGuidance empty() {
            return new DiscoveryGuidance(null, List.of(), List.of(), List.of(), List.of(), null, List.of(), List.of());
        }

        private static <T> List<T> copy(List<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
    }

    public record ConceptRule(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ ]*") String name,
        @Size(max = 2_000) String description,
        @Size(max = 10) List<@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*") String> identityKeys
    ) {
        public ConceptRule {
            identityKeys = identityKeys == null ? List.of() : List.copyOf(identityKeys);
        }
    }

    public record NamingRules(
        @Pattern(regexp = "PASCAL_CASE|UPPER_SNAKE_CASE") String nodeLabels,
        @Pattern(regexp = "UPPER_SNAKE_CASE") String relationshipTypes,
        @Pattern(regexp = "CAMEL_CASE|SNAKE_CASE") String properties
    ) {
    }

    public record PropertyRule(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ ]*") String owner,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*") String name,
        @NotBlank @Pattern(regexp = "STRING|INTEGER|LONG|FLOAT|DOUBLE|BOOLEAN|DATE|DATETIME|LOCAL_DATETIME|LIST_STRING") String type,
        Boolean identity,
        Boolean required
    ) {
    }

    public record RelationshipRule(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ ]*") String type,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ ]*") String from,
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_ ]*") String to,
        @Pattern(regexp = "ONE_TO_ONE|ONE_TO_MANY|MANY_TO_ONE|MANY_TO_MANY") String cardinality
    ) {
    }
}
