package io.github.vfedoriv.graphrag.discovery;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.github.vfedoriv.graphrag.discovery.DiscoveryContracts.FailureCategory;
import io.github.vfedoriv.graphrag.logging.LogMetadata;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.nio.channels.InterruptedByTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class SourceFailureClassifier {
    static final int MAX_CAUSE_CHAIN = 12;

    public SourceFailureDecision classify(Throwable failure) {
        List<Throwable> chain = causeChain(failure);
        Throwable root = chain.isEmpty() ? null : chain.getLast();
        Integer status = providerStatus(chain);
        DecisionCore core = decision(chain, status);
        List<String> types = chain.stream().map(value -> value.getClass().getSimpleName()).toList();
        return new SourceFailureDecision(core.category(), core.code(), core.retryable(), status, types,
            root == null ? "none" : root.getClass().getSimpleName(),
            LogMetadata.exceptionMessageFingerprint(root));
    }

    private DecisionCore decision(List<Throwable> chain, Integer status) {
        ModelOutputException outputFailure = first(chain, ModelOutputException.class);
        if (outputFailure != null) {
            FailureCategory category = outputFailure.failureCode() == SourceFailureCode.MALFORMED_MODEL_RESPONSE
                ? FailureCategory.CONVERSION_ERROR : FailureCategory.VALIDATION_ERROR;
            return new DecisionCore(category, outputFailure.failureCode(), true);
        }
        SourceStateException sourceFailure = first(chain, SourceStateException.class);
        if (sourceFailure != null) {
            return new DecisionCore(FailureCategory.VALIDATION_ERROR, sourceFailure.failureCode(), false);
        }
        if (containsTimeout(chain) || Integer.valueOf(408).equals(status)) {
            return new DecisionCore(FailureCategory.TIMEOUT, SourceFailureCode.TRANSPORT_TIMEOUT, true);
        }
        if (Integer.valueOf(429).equals(status)) {
            return new DecisionCore(FailureCategory.OVERLOADED, SourceFailureCode.RATE_LIMIT, true);
        }
        if (status != null && status >= 500) {
            return new DecisionCore(FailureCategory.PROVIDER_ERROR, SourceFailureCode.PROVIDER_5XX, true);
        }
        if (Integer.valueOf(409).equals(status)) {
            return new DecisionCore(FailureCategory.OVERLOADED, SourceFailureCode.PROVIDER_RETRYABLE_STATUS, true);
        }
        if (status != null && (status == 401 || status == 403)) {
            return new DecisionCore(FailureCategory.PROVIDER_ERROR, SourceFailureCode.PROVIDER_AUTH, false);
        }
        if (status != null && status >= 400) {
            return new DecisionCore(FailureCategory.PROVIDER_ERROR, SourceFailureCode.PROVIDER_INVALID_REQUEST, false);
        }
        if (contains(chain, OpenAIIoException.class) || contains(chain, ConnectException.class)
            || contains(chain, InterruptedIOException.class)) {
            return new DecisionCore(FailureCategory.PROVIDER_ERROR, SourceFailureCode.TRANSPORT_IO, true);
        }
        if (isConfigurationFailure(chain)) {
            return new DecisionCore(FailureCategory.PROVIDER_ERROR, SourceFailureCode.CONFIGURATION_ERROR, false);
        }
        String compatibility = compatibilityText(chain);
        if (compatibility.contains("timeout")) {
            return new DecisionCore(FailureCategory.TIMEOUT, SourceFailureCode.TRANSPORT_TIMEOUT, true);
        }
        if (compatibility.contains("rate limit") || compatibility.contains("overload")
            || compatibility.contains("reject")) {
            return new DecisionCore(FailureCategory.OVERLOADED, SourceFailureCode.RATE_LIMIT, true);
        }
        if (compatibility.contains("json") || compatibility.contains("convert")
            || compatibility.contains("parse")) {
            return new DecisionCore(FailureCategory.CONVERSION_ERROR,
                SourceFailureCode.MALFORMED_MODEL_RESPONSE, true);
        }
        if (compatibility.contains("stale")) {
            return new DecisionCore(FailureCategory.VALIDATION_ERROR, SourceFailureCode.SOURCE_STALE, false);
        }
        if (compatibility.contains("unavailable")) {
            return new DecisionCore(FailureCategory.VALIDATION_ERROR, SourceFailureCode.SOURCE_UNAVAILABLE, false);
        }
        if (compatibility.contains("candidate") || compatibility.contains("validation")
            || compatibility.contains("invalid")) {
            return new DecisionCore(FailureCategory.VALIDATION_ERROR,
                SourceFailureCode.INVALID_MODEL_CANDIDATE, true);
        }
        boolean retryable = contains(chain, RejectedExecutionException.class);
        return new DecisionCore(FailureCategory.PROVIDER_ERROR, SourceFailureCode.PROVIDER_ERROR, retryable);
    }

    private List<Throwable> causeChain(Throwable failure) {
        if (failure == null) {
            return List.of();
        }
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && chain.size() < MAX_CAUSE_CHAIN && seen.add(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return List.copyOf(chain);
    }

    private Integer providerStatus(List<Throwable> chain) {
        OpenAIServiceException serviceException = first(chain, OpenAIServiceException.class);
        return serviceException == null ? null : serviceException.statusCode();
    }

    private boolean containsTimeout(List<Throwable> chain) {
        return contains(chain, TimeoutException.class)
            || contains(chain, SocketTimeoutException.class)
            || contains(chain, HttpTimeoutException.class)
            || contains(chain, InterruptedByTimeoutException.class)
            || contains(chain, HttpRetryException.class);
    }

    private boolean isConfigurationFailure(List<Throwable> chain) {
        String text = compatibilityText(chain);
        return text.contains("not configured") || text.contains("configured") || text.contains("configuration")
            || text.contains("api key") || text.contains("base url");
    }

    private String compatibilityText(List<Throwable> chain) {
        StringBuilder value = new StringBuilder();
        for (Throwable throwable : chain) {
            value.append(throwable.getClass().getName()).append(' ');
            if (throwable.getMessage() != null) {
                value.append(throwable.getMessage()).append(' ');
            }
        }
        return value.toString().toLowerCase(Locale.ROOT);
    }

    private <T extends Throwable> boolean contains(List<Throwable> chain, Class<T> type) {
        return first(chain, type) != null;
    }

    private <T extends Throwable> T first(List<Throwable> chain, Class<T> type) {
        for (Throwable throwable : chain) {
            if (type.isInstance(throwable)) {
                return type.cast(throwable);
            }
        }
        return null;
    }

    private record DecisionCore(FailureCategory category, SourceFailureCode code, boolean retryable) {
    }
}
