package io.github.vfedoriv.graphrag.infrastructure.persistence.relational.repository;

import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.AdvancedSearchResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAdvancedSearchResultRepository extends JpaRepository<AdvancedSearchResultEntity, String> { }
