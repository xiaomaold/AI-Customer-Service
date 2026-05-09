package com.airag.modules.knowledge.mq;

import com.airag.modules.knowledge.service.KnowledgeDocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentTaskListener {

    private final KnowledgeDocumentProcessingService knowledgeDocumentProcessingService;

    @RabbitListener(queues = "${rag.knowledge.async.queue}")
    public void handle(KnowledgeDocumentTaskMessage message) {
        if (message == null || message.getDocumentId() == null || message.getTaskType() == null) {
            log.warn("Ignore invalid knowledge document task message: {}", message);
            return;
        }
        log.info("Consume knowledge document task type={}, documentId={}, knowledgeBaseId={}",
                message.getTaskType(), message.getDocumentId(), message.getKnowledgeBaseId());

        if (message.getTaskType() == KnowledgeDocumentTaskType.REBUILD_INDEX) {
            knowledgeDocumentProcessingService.rebuildIndex(message.getDocumentId());
            return;
        }
        knowledgeDocumentProcessingService.processUploadedDocument(message.getDocumentId());
    }
}
