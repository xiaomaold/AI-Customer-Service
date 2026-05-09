package com.airag.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue knowledgeDocumentProcessQueue(KnowledgeProperties knowledgeProperties) {
        return QueueBuilder.durable(knowledgeProperties.getAsync().getQueue()).build();
    }

    @Bean
    public DirectExchange knowledgeDocumentProcessExchange(KnowledgeProperties knowledgeProperties) {
        return new DirectExchange(knowledgeProperties.getAsync().getExchange(), true, false);
    }

    @Bean
    public Binding knowledgeDocumentProcessBinding(Queue knowledgeDocumentProcessQueue,
                                                   DirectExchange knowledgeDocumentProcessExchange,
                                                   KnowledgeProperties knowledgeProperties) {
        return BindingBuilder.bind(knowledgeDocumentProcessQueue)
                .to(knowledgeDocumentProcessExchange)
                .with(knowledgeProperties.getAsync().getRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter rabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        return rabbitTemplate;
    }
}
