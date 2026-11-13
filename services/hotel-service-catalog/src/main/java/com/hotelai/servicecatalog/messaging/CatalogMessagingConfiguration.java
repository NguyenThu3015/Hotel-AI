package com.hotelai.servicecatalog.messaging;

import com.hotelai.servicecatalog.config.CatalogProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CatalogMessagingConfiguration {
    @Bean
    TopicExchange catalogEventExchange(CatalogProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange catalogDeadLetterExchange(CatalogProperties properties) {
        return new TopicExchange(properties.messaging().deadLetterExchange(), true, false);
    }

    @Bean
    Queue catalogStayQueue(CatalogProperties properties) {
        return QueueBuilder.durable(properties.messaging().stayQueue())
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }

    @Bean
    Binding catalogStayBinding(
            Queue catalogStayQueue,
            TopicExchange catalogEventExchange,
            CatalogProperties properties) {
        return BindingBuilder.bind(catalogStayQueue)
                .to(catalogEventExchange)
                .with(properties.messaging().stayRoutingKey());
    }

    @Bean
    Queue catalogDeadLetterQueue(CatalogProperties properties) {
        return QueueBuilder.durable(properties.messaging().deadLetterQueue()).build();
    }

    @Bean
    Binding catalogDeadLetterBinding(
            Queue catalogDeadLetterQueue,
            TopicExchange catalogDeadLetterExchange,
            CatalogProperties properties) {
        return BindingBuilder.bind(catalogDeadLetterQueue)
                .to(catalogDeadLetterExchange)
                .with(properties.messaging().deadLetterRoutingKey());
    }
}
