package io.github.vfedoriv.graphrag.document.chunking;

public final class ChunkIdentity {

    private ChunkIdentity() {
    }

    public static String childId(String documentContentRevision, ChunkSlice slice) {
        if (documentContentRevision == null || documentContentRevision.isBlank()) {
            throw new IllegalArgumentException("Document content revision must not be blank");
        }
        String identity = String.join(
            "\n",
            documentContentRevision,
            slice.strategyRevision(),
            slice.kind(),
            Integer.toString(slice.sectionIndex()),
            String.valueOf(slice.sourceStart()),
            String.valueOf(slice.sourceEnd())
        );
        return "chunk_" + ChunkHashes.sha256(identity);
    }
}
