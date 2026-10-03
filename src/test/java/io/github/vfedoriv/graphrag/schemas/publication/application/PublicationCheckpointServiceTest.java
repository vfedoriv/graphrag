package io.github.vfedoriv.graphrag.schemas.publication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftPublicationLink;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PublicationCheckpointServiceTest {
    private final SchemaDraftPublicationRepository publications = mock(SchemaDraftPublicationRepository.class);
    private final DraftPublicationLink link = mock(DraftPublicationLink.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus status = new SimpleTransactionStatus();
    private final SchemaDraftPublicationNode publication = new SchemaDraftPublicationNode();
    private final DraftPublicationLink.Completion completion = new DraftPublicationLink.Completion(
        "kb", "draft", 7, "aggregate", 3L, "schema", "hash", Instant.EPOCH);

    @Test void completionSavesPublicationAndLinksDraftBeforeOneCommit() {
        PublicationCheckpointService checkpoint = proxied();
        when(publications.save(publication)).thenReturn(publication);
        assertThat(checkpoint.completePublication(publication, completion)).isSameAs(publication);
        InOrder order = inOrder(transactions, publications, link);
        order.verify(transactions).getTransaction(argThat(value -> value.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
        order.verify(publications).save(publication);
        order.verify(link).complete(completion);
        order.verify(transactions).commit(status);
        verify(transactions, never()).rollback(status);
    }

    @Test void failedDraftLinkRollsBackCompletionInsteadOfCommittingPublication() {
        PublicationCheckpointService checkpoint = proxied();
        when(publications.save(publication)).thenReturn(publication);
        doThrow(new IllegalStateException("changed draft")).when(link).complete(completion);
        assertThatThrownBy(() -> checkpoint.completePublication(publication, completion))
            .isInstanceOf(IllegalStateException.class).hasMessage("changed draft");
        InOrder order = inOrder(publications, link, transactions);
        order.verify(publications).save(publication);
        order.verify(link).complete(completion);
        order.verify(transactions).rollback(status);
        verify(transactions, never()).commit(status);
    }

    @Test void intentCommitsWithoutInvokingDraftLink() {
        PublicationCheckpointService checkpoint = proxied();
        when(publications.save(publication)).thenReturn(publication);
        assertThat(checkpoint.savePublicationIntent(publication)).isSameAs(publication);
        verify(transactions).commit(status);
        verifyNoInteractions(link);
    }

    private PublicationCheckpointService proxied() {
        when(transactions.getTransaction(any())).thenReturn(status);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(new PublicationCheckpointService(publications, link));
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (PublicationCheckpointService) factory.getProxy();
    }
}
