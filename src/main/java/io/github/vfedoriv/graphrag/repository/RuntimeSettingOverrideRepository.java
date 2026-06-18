package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.RuntimeSettingOverrideNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface RuntimeSettingOverrideRepository extends Neo4jRepository<RuntimeSettingOverrideNode, String> {
}
