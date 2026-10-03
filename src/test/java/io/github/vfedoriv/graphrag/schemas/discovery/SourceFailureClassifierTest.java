package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.schemas.discovery.MalformedModelResponseException;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceStateException;
import io.github.vfedoriv.graphrag.schemas.discovery.EmptyModelResponseException;
import io.github.vfedoriv.graphrag.schemas.discovery.InvalidModelCandidateException;
import io.github.vfedoriv.graphrag.schemas.discovery.adapters.model.ModelResponseDiagnostics;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryDeadlineExceededException;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureClassifier;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureCode;
import io.github.vfedoriv.graphrag.schemas.discovery.SourceFailureDecision;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.core.http.Headers;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.UnexpectedStatusCodeException;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.FailureCategory;
import java.net.SocketTimeoutException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SourceFailureClassifierTest {
    private final SourceFailureClassifier classifier = new SourceFailureClassifier();

    @Test
    void findsNestedTimeoutWithoutDependingOnOuterMessage() {
        RuntimeException failure = new RuntimeException("provider failed",
            new RuntimeException("request failed", new SocketTimeoutException("private endpoint details")));

        SourceFailureDecision decision = classifier.classify(failure);

        assertThat(decision.category()).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(decision.code()).isEqualTo(SourceFailureCode.TRANSPORT_TIMEOUT);
        assertThat(decision.retryable()).isTrue();
        assertThat(decision.exceptionTypes())
            .containsExactly("RuntimeException", "RuntimeException", "SocketTimeoutException");
        assertThat(decision.rootExceptionType()).isEqualTo("SocketTimeoutException");
        assertThat(decision.messageFingerprint()).startsWith("sha256:");
    }

    @ParameterizedTest
    @CsvSource({
        "408,TRANSPORT_TIMEOUT,true",
        "409,PROVIDER_RETRYABLE_STATUS,true",
        "429,RATE_LIMIT,true",
        "500,PROVIDER_5XX,true",
        "503,PROVIDER_5XX,true",
        "400,PROVIDER_INVALID_REQUEST,false",
        "401,PROVIDER_AUTH,false",
        "403,PROVIDER_AUTH,false",
        "404,PROVIDER_INVALID_REQUEST,false",
        "422,PROVIDER_INVALID_REQUEST,false"
    })
    void classifiesProviderStatuses(int status, SourceFailureCode code, boolean retryable) {
        SourceFailureDecision decision = classifier.classify(serviceFailure(status));

        assertThat(decision.providerStatus()).isEqualTo(status);
        assertThat(decision.code()).isEqualTo(code);
        assertThat(decision.retryable()).isEqualTo(retryable);
    }

    @Test
    void classifiesTypedOutputSourceAndConfigurationFailures() {
        assertThat(classifier.classify(new EmptyModelResponseException(ModelResponseDiagnostics.none())).code())
            .isEqualTo(SourceFailureCode.EMPTY_MODEL_RESPONSE);
        assertThat(classifier.classify(new MalformedModelResponseException(ModelResponseDiagnostics.none())).code())
            .isEqualTo(SourceFailureCode.MALFORMED_MODEL_RESPONSE);
        assertThat(classifier.classify(new InvalidModelCandidateException(ModelResponseDiagnostics.none())).code())
            .isEqualTo(SourceFailureCode.INVALID_MODEL_CANDIDATE);
        assertThat(classifier.classify(new SourceStateException(SourceFailureCode.SOURCE_STALE)).retryable()).isFalse();
        assertThat(classifier.classify(new SourceStateException(SourceFailureCode.SOURCE_UNAVAILABLE)).retryable()).isFalse();
        SourceFailureDecision configuration = classifier.classify(
            new IllegalStateException("No Spring AI ChatModel bean is configured"));
        assertThat(configuration.code()).isEqualTo(SourceFailureCode.CONFIGURATION_ERROR);
        assertThat(configuration.retryable()).isFalse();
    }

    @Test
    void preservesDistinctRetryableWorkflowDeadlineCodes() {
        SourceFailureDecision source = classifier.classify(
            new DiscoveryDeadlineExceededException(SourceFailureCode.SOURCE_DEADLINE_EXCEEDED));
        SourceFailureDecision request = classifier.classify(
            new DiscoveryDeadlineExceededException(SourceFailureCode.REQUEST_DEADLINE_EXCEEDED));

        assertThat(source.category()).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(source.code()).isEqualTo(SourceFailureCode.SOURCE_DEADLINE_EXCEEDED);
        assertThat(source.retryable()).isTrue();
        assertThat(request.category()).isEqualTo(FailureCategory.TIMEOUT);
        assertThat(request.code()).isEqualTo(SourceFailureCode.REQUEST_DEADLINE_EXCEEDED);
        assertThat(request.retryable()).isTrue();
    }

    @Test
    void boundsAndDeduplicatesCyclicCauseChains() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);

        SourceFailureDecision decision = classifier.classify(first);

        assertThat(decision.exceptionTypes()).hasSize(2);
    }

    private OpenAIServiceException serviceFailure(int status) {
        return UnexpectedStatusCodeException.builder()
            .statusCode(status)
            .headers(Headers.builder().build())
            .error(Optional.empty())
            .build();
    }
}
