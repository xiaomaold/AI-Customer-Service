package com.airag.modules.knowledge.mq;

import com.airag.config.KnowledgeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeDocumentTaskProducer {

    private final RabbitTemplate rabbitTemplate;
    private final KnowledgeProperties knowledgeProperties;

    public void send(KnowledgeDocumentTaskMessage message) {
        rabbitTemplate.convertAndSend(
                knowledgeProperties.getAsync().getExchange(),
                knowledgeProperties.getAsync().getRoutingKey(),
                message
        );
    }
}
