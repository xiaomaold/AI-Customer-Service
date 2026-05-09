package com.airag.modules.knowledge.service.impl;

import com.airag.config.KnowledgeProperties;
import com.airag.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.airag.modules.knowledge.mq.KnowledgeDocumentTaskProducer;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.store.PgVectorKnowledgeEmbeddingStore;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceImplTest {

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private PgVectorKnowledgeEmbeddingStore pgVectorKnowledgeEmbeddingStore;

    @Mock
    private KnowledgeDocumentTaskProducer knowledgeDocumentTaskProducer;

    private KnowledgeDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeDocumentServiceImpl(
                knowledgeDocumentMapper,
                knowledgeBaseService,
                new KnowledgeProperties(),
                pgVectorKnowledgeEmbeddingStore,
                knowledgeDocumentTaskProducer
        );
    }

    @Test
    void listDocuments_shouldReturnEmptyWhenUserHasNoAccessibleKnowledgeBase() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of());

        var result = service.listDocuments(1L, List.of("USER"), null);

        assertTrue(result.isEmpty());
        verify(knowledgeDocumentMapper, never()).selectList(any());
    }

    @Test
    void listDocuments_shouldValidateReadPermissionWhenKnowledgeBaseSpecified() {
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());

        service.listDocuments(1L, List.of("USER"), 99L);

        verify(knowledgeBaseService).assertReadable(1L, List.of("USER"), 99L);
    }

    @Test
    void listDocuments_shouldReadOnlyAccessibleKnowledgeBasesWhenScopeIsGlobal() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of(
                KnowledgeBaseVO.builder().id(10L).knowledgeBaseName("A").build(),
                KnowledgeBaseVO.builder().id(20L).knowledgeBaseName("B").build()
        ));
        when(knowledgeDocumentMapper.selectList(any())).thenReturn(List.of());

        service.listDocuments(1L, List.of("USER"), null);

        verify(knowledgeBaseService).list(1L, List.of("USER"));
        verify(knowledgeDocumentMapper).selectList(any());
    }
}
