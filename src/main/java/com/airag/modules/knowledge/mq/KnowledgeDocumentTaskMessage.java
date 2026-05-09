package com.airag.modules.knowledge.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentTaskMessage {

    private Long documentId;
    private Long knowledgeBaseId;
    private KnowledgeDocumentTaskType taskType;
}
