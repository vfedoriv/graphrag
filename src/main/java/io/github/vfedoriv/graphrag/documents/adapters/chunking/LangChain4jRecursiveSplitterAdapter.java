package io.github.vfedoriv.graphrag.documents.adapters.chunking;

import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.TokenEstimator;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * Keeps the dependency-owned recursive splitter behind a project-owned boundary.
 * ChunkSlice positions remain owned by {@link RecursiveTokenAwareChunkingStrategy}.
 */
public final class LangChain4jRecursiveSplitterAdapter {

    private final DocumentSplitter splitter;

    public LangChain4jRecursiveSplitterAdapter(ChunkingContext context) {
        TokenCountEstimator estimator = new ProjectTokenCountEstimator(context.tokenEstimator());
        int sourceTargetTokens = Math.max(1, context.targetTokens() - context.contextHeaderMaxTokens());
        int sourceOverlapTokens = Math.min(context.overlapTokens(), Math.max(0, sourceTargetTokens - 1));
        this.splitter = DocumentSplitters.recursive(
            sourceTargetTokens,
            sourceOverlapTokens,
            estimator
        );
    }

    public int referenceSegmentCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return splitter.split(Document.from(text)).size();
    }

    private record ProjectTokenCountEstimator(TokenEstimator delegate) implements TokenCountEstimator {

        @Override
        public int estimateTokenCountInText(String text) {
            return delegate.count(text);
        }

        @Override
        public int estimateTokenCountInMessage(dev.langchain4j.data.message.ChatMessage message) {
            return delegate.count(String.valueOf(message));
        }

        @Override
        public int estimateTokenCountInMessages(
            Iterable<dev.langchain4j.data.message.ChatMessage> messages
        ) {
            int total = 0;
            for (dev.langchain4j.data.message.ChatMessage message : messages) {
                total += estimateTokenCountInMessage(message);
            }
            return total;
        }
    }
}
