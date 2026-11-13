package com.hotelai.reporting.messaging;

import com.hotelai.reporting.config.ReportingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ReportingMessagingConfiguration {
    @Bean
    TopicExchange reportingEventExchange(ReportingProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange reportingDeadLetterExchange(ReportingProperties properties) {
        return new TopicExchange(
                properties.messaging().deadLetterExchange(), true, false);
    }

    @Bean
    Queue reportingEventQueue(ReportingProperties properties) {
        return QueueBuilder.durable(properties.messaging().queue())
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }

    @Bean
    Binding reportingEventBinding(
            Queue reportingEventQueue,
            TopicExchange reportingEventExchange,
            ReportingProperties properties) {
        return BindingBuilder.bind(reportingEventQueue)
                .to(reportingEventExchange)
                .with(properties.messaging().routingKey());
    }

    @Bean
    Queue reportingDeadLetterQueue(ReportingProperties properties) {
        return QueueBuilder.durable(
                properties.messaging().deadLetterQueue()).build();
    }

    @Bean
    Binding reportingDeadLetterBinding(
            Queue reportingDeadLetterQueue,
            TopicExchange reportingDeadLetterExchange,
            ReportingProperties properties) {
        return BindingBuilder.bind(reportingDeadLetterQueue)
                .to(reportingDeadLetterExchange)
                .with(properties.messaging().deadLetterRoutingKey());
    }
}
