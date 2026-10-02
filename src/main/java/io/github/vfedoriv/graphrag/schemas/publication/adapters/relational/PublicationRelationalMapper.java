package io.github.vfedoriv.graphrag.schemas.publication.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.entity.SchemaDraftPublicationEntity;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import org.springframework.beans.BeanUtils;

final class PublicationRelationalMapper {
    private PublicationRelationalMapper() { }

    static SchemaDraftPublicationNode toDomain(SchemaDraftPublicationEntity source) {
        SchemaDraftPublicationNode target = new SchemaDraftPublicationNode();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    static SchemaDraftPublicationEntity toEntity(SchemaDraftPublicationNode source) {
        SchemaDraftPublicationEntity target = new SchemaDraftPublicationEntity();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
