package io.github.vfedoriv.graphrag.document.chunking;

import io.github.vfedoriv.graphrag.document.ParsedBlock;
import io.github.vfedoriv.graphrag.document.ParsedBlockKind;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecursiveTokenAwareChunkingStrategy implements ChunkingStrategy {

    public static final String NAME = "recursive";
    public static final String REVISION = "recursive-token-aware-v1";
    private static final Pattern PARAGRAPH_BOUNDARY = Pattern.compile("(?:\\R\\s*){2,}");
    private static final Pattern LINE_BOUNDARY = Pattern.compile("\\R");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\s+");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String revision() {
        return REVISION;
    }

    @Override
    public List<ChunkSlice> split(ParsedSection section, ChunkingContext context) {
        String source = section == null || section.text() == null ? "" : section.text();
        if (source.isBlank()) {
            return List.of();
        }
        boolean knownEnglish = isKnownEnglish(section);
        List<Unit> structuralUnits = structuralUnits(section);
        List<Unit> boundedUnits = new ArrayList<>();
        for (Unit unit : structuralUnits) {
            boundedUnits.addAll(subdivide(source, unit, context, knownEnglish, Tier.PARAGRAPH));
        }

        LangChain4jRecursiveSplitterAdapter adapter = new LangChain4jRecursiveSplitterAdapter(context);
        int referenceSegmentCount = adapter.referenceSegmentCount(source);
        List<PackedChunk> packed = pack(source, boundedUnits, context);
        List<ChunkSlice> slices = new ArrayList<>(packed.size());
        for (int index = 0; index < packed.size(); index++) {
            PackedChunk chunk = packed.get(index);
            String text = source.substring(chunk.start(), chunk.end());
            Map<String, Object> diagnostics = new LinkedHashMap<>();
            diagnostics.put("fallbackTier", chunk.deepestTier().apiValue());
            diagnostics.put("knownEnglish", knownEnglish);
            diagnostics.put("langChainReferenceSegments", referenceSegmentCount);
            diagnostics.put("overlapTokens", chunk.overlapTokens());
            diagnostics.put("structuralUnits", chunk.unitCount());
            slices.add(new ChunkSlice(
                text,
                chunk.start(),
                chunk.end(),
                context.tokenEstimator().count(text),
                context.strategyName(),
                context.strategyRevision(),
                context.tokenEstimator().tokenizerId(),
                context.tokenEstimator().countMode(),
                context.settingsHash(),
                context.effectiveRevision(),
                "CHILD",
                section.sectionIndex(),
                index,
                section.pageNumber(),
                section.pageNumber(),
                chunk.structuralPath(),
                chunk.blockConfidence(),
                ChunkHashes.sha256(text),
                diagnostics
            ));
        }
        return List.copyOf(slices);
    }

    private List<Unit> structuralUnits(ParsedSection section) {
        String source = section.text();
        List<ParsedBlock> candidates = section.blocks().stream()
            .filter(block -> block.kind() != ParsedBlockKind.PAGE)
            .filter(block -> block.kind() != ParsedBlockKind.TABLE)
            .filter(block -> block.kind() != ParsedBlockKind.TABLE_CELL)
            .sorted(Comparator.comparingInt(block -> block.sourceRange().startInclusive()))
            .toList();
        if (candidates.isEmpty()) {
            return List.of(new Unit(0, source.length(), Tier.STRUCTURE, List.of(), null));
        }
        List<Unit> units = new ArrayList<>();
        int cursor = 0;
        for (ParsedBlock block : candidates) {
            int start = block.sourceRange().startInclusive();
            int end = block.sourceRange().endExclusive();
            if (start < cursor) {
                continue;
            }
            if (start > cursor) {
                units.add(new Unit(cursor, start, Tier.STRUCTURE, List.of(), null));
            }
            units.add(new Unit(
                start,
                end,
                Tier.STRUCTURE,
                block.structuralPath().segments(),
                block.confidence().name()
            ));
            cursor = end;
        }
        if (cursor < source.length()) {
            units.add(new Unit(cursor, source.length(), Tier.STRUCTURE, List.of(), null));
        }
        return units;
    }

    private List<Unit> subdivide(
        String source,
        Unit unit,
        ChunkingContext context,
        boolean knownEnglish,
        Tier tier
    ) {
        if (fits(source, unit.start(), unit.end(), context)) {
            return List.of(unit);
        }
        if (tier == Tier.CHARACTER) {
            return characterUnits(source, unit, context);
        }
        Tier effectiveTier = !knownEnglish && tier == Tier.SENTENCE ? Tier.WORD : tier;
        List<Range> ranges = ranges(source, unit.start(), unit.end(), effectiveTier);
        Tier next = effectiveTier.next();
        if (ranges.size() < 2) {
            return subdivide(source, unit.withTier(effectiveTier), context, knownEnglish, next);
        }
        List<Unit> result = new ArrayList<>();
        for (Range range : ranges) {
            result.addAll(subdivide(
                source,
                new Unit(
                    range.start(),
                    range.end(),
                    effectiveTier,
                    unit.structuralPath(),
                    unit.blockConfidence()
                ),
                context,
                knownEnglish,
                next
            ));
        }
        return result;
    }

    private List<Range> ranges(String source, int start, int end, Tier tier) {
        if (tier == Tier.SENTENCE) {
            return sentenceRanges(source, start, end);
        }
        Pattern boundary = switch (tier) {
            case PARAGRAPH -> PARAGRAPH_BOUNDARY;
            case LINE -> LINE_BOUNDARY;
            case WORD -> WORD_BOUNDARY;
            default -> null;
        };
        if (boundary == null) {
            return List.of(new Range(start, end));
        }
        List<Range> ranges = new ArrayList<>();
        Matcher matcher = boundary.matcher(source);
        matcher.region(start, end);
        int cursor = start;
        while (matcher.find()) {
            int boundaryEnd = matcher.end();
            if (boundaryEnd > cursor) {
                ranges.add(new Range(cursor, boundaryEnd));
                cursor = boundaryEnd;
            }
        }
        if (cursor < end) {
            ranges.add(new Range(cursor, end));
        }
        return ranges;
    }

    private List<Range> sentenceRanges(String source, int start, int end) {
        String text = source.substring(start, end);
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(text);
        List<Range> ranges = new ArrayList<>();
        int localStart = iterator.first();
        for (int localEnd = iterator.next(); localEnd != BreakIterator.DONE; localEnd = iterator.next()) {
            ranges.add(new Range(start + localStart, start + localEnd));
            localStart = localEnd;
        }
        return ranges;
    }

    private List<Unit> characterUnits(String source, Unit unit, ChunkingContext context) {
        List<Unit> units = new ArrayList<>();
        int start = unit.start();
        while (start < unit.end()) {
            int end = Math.min(unit.end(), start + sourceCharacterLimit(context));
            while (end > start
                && context.tokenEstimator().count(source.substring(start, end)) > sourceTokenLimit(context)) {
                end--;
            }
            if (end < unit.end() && end > start
                && Character.isHighSurrogate(source.charAt(end - 1))
                && Character.isLowSurrogate(source.charAt(end))) {
                end--;
            }
            if (end <= start) {
                end = Math.min(unit.end(), start + Character.charCount(source.codePointAt(start)));
            }
            units.add(new Unit(
                start,
                end,
                Tier.CHARACTER,
                unit.structuralPath(),
                unit.blockConfidence()
            ));
            start = end;
        }
        return units;
    }

    private List<PackedChunk> pack(String source, List<Unit> units, ChunkingContext context) {
        List<PackedChunk> chunks = new ArrayList<>();
        List<Unit> current = new ArrayList<>();
        int overlapTokens = 0;
        for (Unit unit : units) {
            if (!current.isEmpty() && !fits(source, current.getFirst().start(), unit.end(), context)) {
                chunks.add(toPacked(current, overlapTokens));
                List<Unit> overlap = trailingOverlap(source, current, context);
                current = new ArrayList<>(overlap);
                overlapTokens = tokenCount(source, overlap, context);
                while (!current.isEmpty() && !fits(source, current.getFirst().start(), unit.end(), context)) {
                    current.removeFirst();
                    overlapTokens = tokenCount(source, current, context);
                }
            }
            current.add(unit);
        }
        if (!current.isEmpty()) {
            chunks.add(toPacked(current, overlapTokens));
        }
        return chunks;
    }

    private List<Unit> trailingOverlap(String source, List<Unit> units, ChunkingContext context) {
        if (context.overlapTokens() == 0 || units.size() < 2) {
            return List.of();
        }
        List<Unit> overlap = new ArrayList<>();
        for (int index = units.size() - 1; index >= 0; index--) {
            Unit candidate = units.get(index);
            List<Unit> prospective = new ArrayList<>();
            prospective.add(candidate);
            prospective.addAll(overlap);
            int tokens = tokenCount(source, prospective, context);
            int characters = prospective.getLast().end() - prospective.getFirst().start();
            if (tokens > context.overlapTokens() || characters > context.hardCharacterLimit()) {
                break;
            }
            overlap = prospective;
        }
        return List.copyOf(overlap);
    }

    private PackedChunk toPacked(List<Unit> units, int overlapTokens) {
        Unit first = units.getFirst();
        Unit last = units.getLast();
        Unit provenance = units.stream()
            .filter(unit -> !unit.structuralPath().isEmpty() || unit.blockConfidence() != null)
            .findFirst()
            .orElse(first);
        Tier deepestTier = units.stream()
            .map(Unit::tier)
            .max(Comparator.comparingInt(Enum::ordinal))
            .orElse(Tier.STRUCTURE);
        return new PackedChunk(
            first.start(),
            last.end(),
            units.size(),
            overlapTokens,
            deepestTier,
            provenance.structuralPath(),
            provenance.blockConfidence()
        );
    }

    private boolean fits(String source, int start, int end, ChunkingContext context) {
        return end - start <= sourceCharacterLimit(context)
            && context.tokenEstimator().count(source.substring(start, end)) <= sourceTokenLimit(context);
    }

    private int sourceTokenLimit(ChunkingContext context) {
        return Math.max(1, context.targetTokens() - context.contextHeaderMaxTokens());
    }

    private int sourceCharacterLimit(ChunkingContext context) {
        return Math.max(1, context.hardCharacterLimit() - context.contextHeaderMaxCharacters());
    }

    private int tokenCount(String source, List<Unit> units, ChunkingContext context) {
        if (units.isEmpty()) {
            return 0;
        }
        String text = source.substring(units.getFirst().start(), units.getLast().end());
        return context.tokenEstimator().count(text);
    }

    private boolean isKnownEnglish(ParsedSection section) {
        if (section == null) {
            return false;
        }
        Object language = section.metadata().get("language");
        if (language == null) {
            language = section.metadata().get("languageCode");
        }
        return language != null && String.valueOf(language).toLowerCase(Locale.ROOT).startsWith("en");
    }

    private enum Tier {
        STRUCTURE("structure"),
        PARAGRAPH("paragraph"),
        LINE("line"),
        SENTENCE("sentence"),
        WORD("word"),
        CHARACTER("character");

        private final String apiValue;

        Tier(String apiValue) {
            this.apiValue = apiValue;
        }

        String apiValue() {
            return apiValue;
        }

        Tier next() {
            return switch (this) {
                case STRUCTURE -> PARAGRAPH;
                case PARAGRAPH -> LINE;
                case LINE -> SENTENCE;
                case SENTENCE -> WORD;
                case WORD, CHARACTER -> CHARACTER;
            };
        }
    }

    private record Range(int start, int end) {
    }

    private record Unit(
        int start,
        int end,
        Tier tier,
        List<String> structuralPath,
        String blockConfidence
    ) {
        Unit withTier(Tier replacement) {
            return new Unit(start, end, replacement, structuralPath, blockConfidence);
        }

    }

    private record PackedChunk(
        int start,
        int end,
        int unitCount,
        int overlapTokens,
        Tier deepestTier,
        List<String> structuralPath,
        String blockConfidence
    ) {
    }
}
