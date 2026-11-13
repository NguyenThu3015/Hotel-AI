package com.hotelai.inventory.messaging;

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
public class RabbitMessagingConfiguration {
    @Bean("inventoryEventsExchange")
    TopicExchange eventsExchange(@Value("${hotel.messaging.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean("inventoryDeadLetterExchange")
    TopicExchange deadLetterExchange(@Value("${hotel.messaging.dead-letter-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean("pricingSnapshotQueue")
    Queue pricingSnapshotQueue(
            @Value("${hotel.messaging.pricing-queue}") String name,
            @Value("${hotel.messaging.dead-letter-exchange}") String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(name + ".dead")
                .build();
    }

    @Bean("pricingSnapshotDeadLetterQueue")
    Queue pricingSnapshotDeadLetterQueue(@Value("${hotel.messaging.pricing-queue}") String name) {
        return QueueBuilder.durable(name + ".dlq").build();
    }

    @Bean
    Binding pricingRateBinding(
            @Qualifier("pricingSnapshotQueue") Queue queue,
            @Qualifier("inventoryEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("pricing.room-type-price-published.v1");
    }

    @Bean
    Binding pricingPolicyBinding(
            @Qualifier("pricingSnapshotQueue") Queue queue,
            @Qualifier("inventoryEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("pricing.room-type-policy-published.v1");
    }

    @Bean
    Binding pricingDeadLetterBinding(
            @Qualifier("pricingSnapshotDeadLetterQueue") Queue queue,
            @Qualifier("inventoryDeadLetterExchange") TopicExchange exchange,
            @Value("${hotel.messaging.pricing-queue}") String sourceQueue) {
        return BindingBuilder.bind(queue).to(exchange).with(sourceQueue + ".dead");
    }
}
