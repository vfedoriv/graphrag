package io.github.vfedoriv.graphrag.documents.contracts;

public interface DocumentSourceInputs {
    Source readOwned(String knowledgeBaseId, String documentId);

    String parse(String filename, String contentType, byte[] bytes);

    record Source(String documentId, String filename, String contentType, byte[] bytes) {
        public Source {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        public int byteCount() {
            return bytes.length;
        }
    }
}
