package io.github.vfedoriv.graphrag.error;

public class QueryDeadlineExceededException extends RuntimeException {

    public QueryDeadlineExceededException(Throwable cause) {
        super("The query exceeded its execution deadline", cause);
    }
}
