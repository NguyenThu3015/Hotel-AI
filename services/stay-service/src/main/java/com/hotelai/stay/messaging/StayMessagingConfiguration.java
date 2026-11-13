package com.hotelai.stay.messaging;

import com.hotelai.stay.config.StayProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class StayMessagingConfiguration {
    @Bean
    TopicExchange stayEventExchange(StayProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange stayDeadLetterExchange(StayProperties properties) {
        return new TopicExchange(properties.messaging().deadLetterExchange(), true, false);
    }

    @Bean
    Queue stayReservationQueue(StayProperties properties) {
        return sourceQueue(properties.messaging().reservationQueue(), properties);
    }

    @Bean
    Queue stayInventoryQueue(StayProperties properties) {
        return sourceQueue(properties.messaging().inventoryQueue(), properties);
    }

    @Bean
    Queue stayServiceUsageQueue(StayProperties properties) {
        return sourceQueue(properties.messaging().serviceUsageQueue(), properties);
    }

    @Bean
    Binding stayReservationBinding(
            Queue stayReservationQueue, TopicExchange stayEventExchange, StayProperties properties) {
        return BindingBuilder.bind(stayReservationQueue)
                .to(stayEventExchange)
                .with(properties.messaging().reservationRoutingKey());
    }

    @Bean
    Binding stayInventoryBinding(
            Queue stayInventoryQueue, TopicExchange stayEventExchange, StayProperties properties) {
        return BindingBuilder.bind(stayInventoryQueue)
                .to(stayEventExchange)
                .with(properties.messaging().inventoryRoutingKey());
    }

    @Bean
    Binding stayServiceUsageBinding(
            Queue stayServiceUsageQueue,
            TopicExchange stayEventExchange,
            StayProperties properties) {
        return BindingBuilder.bind(stayServiceUsageQueue)
                .to(stayEventExchange)
                .with(properties.messaging().serviceUsageRoutingKey());
    }

    @Bean
    Queue stayDeadLetterQueue(StayProperties properties) {
        return QueueBuilder.durable(properties.messaging().deadLetterQueue()).build();
    }

    @Bean
    Binding stayDeadLetterBinding(
            Queue stayDeadLetterQueue,
            TopicExchange stayDeadLetterExchange,
            StayProperties properties) {
        return BindingBuilder.bind(stayDeadLetterQueue)
                .to(stayDeadLetterExchange)
                .with(properties.messaging().deadLetterRoutingKey());
    }

    private static Queue sourceQueue(String name, StayProperties properties) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }
}
