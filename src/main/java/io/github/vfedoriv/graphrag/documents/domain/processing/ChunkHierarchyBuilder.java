package io.github.vfedoriv.graphrag.documents.domain.processing;

import java.util.function.BiFunction;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkHashes;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkKind;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChunkHierarchyBuilder {

    private final BiFunction<ParsedSection, ChunkingContext, List<ChunkSlice>> splitter;

    public ChunkHierarchyBuilder(BiFunction<ParsedSection, ChunkingContext, List<ChunkSlice>> splitter) {
        this.splitter = splitter;
    }

    public HierarchyPlan build(ParsedDocument document, ChunkingContext context) {
        List<ChildPlan> children = new ArrayList<>();
        for (ParsedSection section : document.sections()) {
            for (ChunkSlice slice : splitter.apply(section, context)) {
                children.add(new ChildPlan(section, slice));
            }
        }
        if (!RecursiveTokenAwareChunkingStrategy.NAME.equals(context.strategyName())) {
            return new HierarchyPlan(List.of(), List.copyOf(children));
        }

        List<ParentSeed> seeds = sameSectionParents(children, context);
        List<ParentSeed> merged = mergeCompatiblePdfContinuations(seeds, document, context);
        List<ParentPlan> parents = new ArrayList<>();
        for (int parentIndex = 0; parentIndex < merged.size(); parentIndex++) {
            ParentSeed seed = merged.get(parentIndex);
            parents.add(new ParentPlan(parentSlice(seed, parentIndex, context), seed.text(), seed.children()));
        }
        return new HierarchyPlan(List.copyOf(parents), List.copyOf(children));
    }

    private List<ParentSeed> sameSectionParents(List<ChildPlan> children, ChunkingContext context) {
        List<ParentSeed> parents = new ArrayList<>();
        List<ChildPlan> current = new ArrayList<>();
        for (ChildPlan child : children) {
            if (!current.isEmpty() && (!sameSection(current.getFirst(), child)
                || !structurallyCompatible(current.getFirst().slice(), child.slice())
                || !fits(current, child, context))) {
                parents.add(parentSeed(current));
                current = new ArrayList<>();
            }
            current.add(child);
        }
        if (!current.isEmpty()) {
            parents.add(parentSeed(current));
        }
        return parents;
    }

    private boolean sameSection(ChildPlan first, ChildPlan next) {
        return first.section().sectionIndex() == next.section().sectionIndex();
    }

    private boolean structurallyCompatible(ChunkSlice first, ChunkSlice next) {
        return first.structuralPath().isEmpty()
            || next.structuralPath().isEmpty()
            || first.structuralPath().equals(next.structuralPath());
    }

    private boolean fits(List<ChildPlan> current, ChildPlan candidate, ChunkingContext context) {
        ParsedSection section = current.getFirst().section();
        int start = requiredStart(current.getFirst().slice());
        int end = requiredEnd(candidate.slice());
        String text = section.text().substring(start, end);
        return text.length() <= context.parentHardCharacterLimit()
            && context.tokenEstimator().count(text) <= context.parentTargetTokens();
    }

    private ParentSeed parentSeed(List<ChildPlan> children) {
        ChildPlan first = children.getFirst();
        ChildPlan last = children.getLast();
        int start = requiredStart(first.slice());
        int end = requiredEnd(last.slice());
        String text = first.section().text().substring(start, end);
        List<String> path = children.stream()
            .map(ChildPlan::slice)
            .map(ChunkSlice::structuralPath)
            .filter(value -> !value.isEmpty())
            .findFirst()
            .orElse(List.of());
        String confidence = children.stream()
            .map(ChildPlan::slice)
            .map(ChunkSlice::blockConfidence)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
        return new ParentSeed(
            text,
            first.section().sectionIndex(),
            start,
            end,
            first.slice().pageStart(),
            last.slice().pageEnd(),
            path,
            confidence,
            List.copyOf(children),
            false
        );
    }

    private List<ParentSeed> mergeCompatiblePdfContinuations(
        List<ParentSeed> seeds,
        ParsedDocument document,
        ChunkingContext context
    ) {
        if (!"PDF".equalsIgnoreCase(document.format()) || context.parentMaxPages() < 2) {
            return seeds;
        }
        List<ParentSeed> merged = new ArrayList<>();
        int index = 0;
        while (index < seeds.size()) {
            ParentSeed current = seeds.get(index);
            if (index + 1 < seeds.size()) {
                ParentSeed next = seeds.get(index + 1);
                ParentSeed crossPage = mergeIfCompatible(current, next, context);
                if (crossPage != null) {
                    merged.add(crossPage);
                    index += 2;
                    continue;
                }
            }
            merged.add(current);
            index++;
        }
        return merged;
    }

    private ParentSeed mergeIfCompatible(ParentSeed left, ParentSeed right, ChunkingContext context) {
        if (left.pageStart() == null || left.pageEnd() == null || right.pageStart() == null || right.pageEnd() == null
            || right.pageStart() != left.pageEnd() + 1
            || left.pageStart().equals(right.pageEnd())
            || left.structuralPath().isEmpty()
            || !left.structuralPath().equals(right.structuralPath())
            || !"AUTHORITATIVE".equals(left.blockConfidence())
            || !"AUTHORITATIVE".equals(right.blockConfidence())) {
            return null;
        }
        String text = left.text() + System.lineSeparator() + right.text();
        if (text.length() > context.parentHardCharacterLimit()
            || context.tokenEstimator().count(text) > context.parentTargetTokens()) {
            return null;
        }
        List<ChildPlan> children = new ArrayList<>(left.children());
        children.addAll(right.children());
        return new ParentSeed(
            text,
            left.sectionIndex(),
            left.sourceStart(),
            right.sourceEnd(),
            left.pageStart(),
            right.pageEnd(),
            left.structuralPath(),
            left.blockConfidence(),
            List.copyOf(children),
            true
        );
    }

    private ChunkSlice parentSlice(ParentSeed parent, int parentIndex, ChunkingContext context) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("childCount", parent.children().size());
        diagnostics.put("crossPage", parent.crossPage());
        diagnostics.put("materializedFromSourceRanges", true);
        return new ChunkSlice(
            parent.text(),
            parent.sourceStart(),
            parent.sourceEnd(),
            context.tokenEstimator().count(parent.text()),
            context.strategyName(),
            context.strategyRevision(),
            context.tokenEstimator().tokenizerId(),
            context.tokenEstimator().countMode(),
            context.settingsHash(),
            context.effectiveRevision(),
            ChunkKind.PARENT.name(),
            parent.sectionIndex(),
            parentIndex,
            parent.pageStart(),
            parent.pageEnd(),
            parent.structuralPath(),
            parent.blockConfidence(),
            ChunkHashes.sha256(parent.text()),
            Map.copyOf(diagnostics)
        );
    }

    private int requiredStart(ChunkSlice slice) {
        if (slice.sourceStart() == null) {
            throw new IllegalArgumentException("Hierarchical chunks require tracked source ranges");
        }
        return slice.sourceStart();
    }

    private int requiredEnd(ChunkSlice slice) {
        if (slice.sourceEnd() == null) {
            throw new IllegalArgumentException("Hierarchical chunks require tracked source ranges");
        }
        return slice.sourceEnd();
    }

    public record HierarchyPlan(List<ParentPlan> parents, List<ChildPlan> flatChildren) {
    }

    public record ParentPlan(ChunkSlice slice, String sourceText, List<ChildPlan> children) {
    }

    public record ChildPlan(ParsedSection section, ChunkSlice slice) {
    }

    private record ParentSeed(
        String text,
        int sectionIndex,
        Integer sourceStart,
        Integer sourceEnd,
        Integer pageStart,
        Integer pageEnd,
        List<String> structuralPath,
        String blockConfidence,
        List<ChildPlan> children,
        boolean crossPage
    ) {
    }
}
