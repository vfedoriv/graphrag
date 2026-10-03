package io.github.vfedoriv.graphrag.http.contracts;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PageResponse", description = "Zero-based bounded page of ordered results.")
public class PageResponse<T> {
    private final int page;
    private final int size;
    private final long totalElements;
    private final List<T> content;

    public PageResponse(int page, int size, long totalElements, List<T> content) {
        this.page = Math.max(0, page);
        this.size = Math.max(1, Math.min(size, 100));
        this.totalElements = Math.max(0, totalElements);
        this.content = content == null ? List.of() : List.copyOf(content);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public List<T> getContent() {
        return content;
    }
}
