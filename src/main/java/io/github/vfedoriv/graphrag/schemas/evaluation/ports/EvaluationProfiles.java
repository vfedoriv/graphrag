package io.github.vfedoriv.graphrag.schemas.evaluation.ports;

public interface EvaluationProfiles {
    Profile activeProfile(String knowledgeBaseId);
    record Profile(String id, long revision) { }
}
