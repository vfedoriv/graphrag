package io.github.vfedoriv.graphrag.search.runs.adapters.relational.repository;

import io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity.AdvancedSearchResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAdvancedSearchResultRepository extends JpaRepository<AdvancedSearchResultEntity, String> { }
