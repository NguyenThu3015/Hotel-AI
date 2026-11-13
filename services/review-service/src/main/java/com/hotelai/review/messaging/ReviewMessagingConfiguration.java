package com.hotelai.review.messaging;

import com.hotelai.review.config.ReviewProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ReviewMessagingConfiguration {
    @Bean
    TopicExchange reviewEventExchange(ReviewProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange reviewDeadLetterExchange(ReviewProperties properties) {
        return new TopicExchange(
                properties.messaging().deadLetterExchange(), true, false);
    }

    @Bean
    Queue reviewStayQueue(ReviewProperties properties) {
        return sourceQueue(properties.messaging().stayQueue(), properties);
    }

    @Bean
    Queue reviewCustomerQueue(ReviewProperties properties) {
        return sourceQueue(properties.messaging().customerQueue(), properties);
    }

    @Bean
    Binding reviewStayBinding(
            Queue reviewStayQueue,
            TopicExchange reviewEventExchange,
            ReviewProperties properties) {
        return BindingBuilder.bind(reviewStayQueue)
                .to(reviewEventExchange)
                .with(properties.messaging().stayRoutingKey());
    }

    @Bean
    Binding reviewCustomerBinding(
            Queue reviewCustomerQueue,
            TopicExchange reviewEventExchange,
            ReviewProperties properties) {
        return BindingBuilder.bind(reviewCustomerQueue)
                .to(reviewEventExchange)
                .with(properties.messaging().customerRoutingKey());
    }

    @Bean
    Queue reviewDeadLetterQueue(ReviewProperties properties) {
        return QueueBuilder.durable(
                properties.messaging().deadLetterQueue()).build();
    }

    @Bean
    Binding reviewDeadLetterBinding(
            Queue reviewDeadLetterQueue,
            TopicExchange reviewDeadLetterExchange,
            ReviewProperties properties) {
        return BindingBuilder.bind(reviewDeadLetterQueue)
                .to(reviewDeadLetterExchange)
                .with(properties.messaging().deadLetterRoutingKey());
    }

    private static Queue sourceQueue(
            String name, ReviewProperties properties) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }
}
