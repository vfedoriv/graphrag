package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

public interface RuntimeSettingOverrideRepository extends Neo4jRepository<RuntimeSettingOverrideNode, String> {

    @Query("""
        MATCH (override:RuntimeSettingOverride)
        WHERE override.version IS NULL
        SET override.version = 0
        """)
    void backfillMissingVersions();
}
