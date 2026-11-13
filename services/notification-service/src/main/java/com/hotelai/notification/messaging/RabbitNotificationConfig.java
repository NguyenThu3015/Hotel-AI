package com.hotelai.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitNotificationConfig {
    @Bean
    TopicExchange notificationExchange(@Value("${hotel.notification.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    TopicExchange notificationDeadLetterExchange(@Value("${hotel.notification.dead-letter-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue notificationQueue(
            @Value("${hotel.notification.queue}") String name,
            @Value("${hotel.notification.dead-letter-exchange}") String dlx,
            @Value("${hotel.notification.dead-letter-routing-key}") String dlqRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    Queue notificationDeadLetterQueue(@Value("${hotel.notification.dead-letter-queue}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    Binding notificationBinding(
            @Qualifier("notificationQueue") Queue notificationQueue,
            TopicExchange notificationExchange,
            @Value("${hotel.notification.routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    Binding identityPasswordResetBinding(
            @Qualifier("notificationQueue") Queue notificationQueue,
            TopicExchange notificationExchange,
            @Value("${hotel.notification.identity-password-reset-routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    Binding notificationDeadLetterBinding(
            @Qualifier("notificationDeadLetterQueue") Queue notificationDeadLetterQueue,
            TopicExchange notificationDeadLetterExchange,
            @Value("${hotel.notification.dead-letter-routing-key}") String routingKey) {
        return BindingBuilder.bind(notificationDeadLetterQueue).to(notificationDeadLetterExchange).with(routingKey);
    }
}
