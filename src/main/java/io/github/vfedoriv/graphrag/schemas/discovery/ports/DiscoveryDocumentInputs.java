package io.github.vfedoriv.graphrag.schemas.discovery.ports;

public interface DiscoveryDocumentInputs {
    Source readOwned(String knowledgeBaseId, String documentId);

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
