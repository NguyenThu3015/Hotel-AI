package com.hotelai.billing.messaging;

import com.hotelai.billing.config.BillingProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class BillingMessagingConfiguration {
    @Bean
    TopicExchange billingEventExchange(BillingProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange billingDeadLetterExchange(BillingProperties properties) {
        return new TopicExchange(properties.messaging().deadLetterExchange(), true, false);
    }

    @Bean
    Queue billingReservationQueue(BillingProperties properties) {
        return sourceQueue(properties.messaging().reservationQueue(), properties);
    }

    @Bean
    Queue billingStayQueue(BillingProperties properties) {
        return sourceQueue(properties.messaging().stayQueue(), properties);
    }

    @Bean
    Queue billingPaymentQueue(BillingProperties properties) {
        return sourceQueue(properties.messaging().paymentQueue(), properties);
    }

    @Bean
    Binding billingReservationBinding(
            Queue billingReservationQueue,
            TopicExchange billingEventExchange,
            BillingProperties properties) {
        return BindingBuilder.bind(billingReservationQueue)
                .to(billingEventExchange)
                .with(properties.messaging().reservationRoutingKey());
    }

    @Bean
    Binding billingStayBinding(
            Queue billingStayQueue,
            TopicExchange billingEventExchange,
            BillingProperties properties) {
        return BindingBuilder.bind(billingStayQueue)
                .to(billingEventExchange)
                .with(properties.messaging().stayRoutingKey());
    }

    @Bean
    Binding billingPaymentBinding(
            Queue billingPaymentQueue,
            TopicExchange billingEventExchange,
            BillingProperties properties) {
        return BindingBuilder.bind(billingPaymentQueue)
                .to(billingEventExchange)
                .with(properties.messaging().paymentRoutingKey());
    }

    @Bean
    Queue billingDeadLetterQueue(BillingProperties properties) {
        return QueueBuilder.durable(properties.messaging().deadLetterQueue()).build();
    }

    @Bean
    Binding billingDeadLetterBinding(
            Queue billingDeadLetterQueue,
            TopicExchange billingDeadLetterExchange,
            BillingProperties properties) {
        return BindingBuilder.bind(billingDeadLetterQueue)
                .to(billingDeadLetterExchange)
                .with(properties.messaging().deadLetterRoutingKey());
    }

    private static Queue sourceQueue(String name, BillingProperties properties) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }
}
