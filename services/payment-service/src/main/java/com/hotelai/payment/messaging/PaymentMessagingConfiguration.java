package com.hotelai.payment.messaging;

import com.hotelai.payment.config.PaymentProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PaymentMessagingConfiguration {
    @Bean
    TopicExchange paymentEventExchange(PaymentProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange paymentDeadLetterExchange(PaymentProperties properties) {
        return new TopicExchange(properties.messaging().deadLetterExchange(), true, false);
    }

    @Bean
    Queue paymentReservationQueue(PaymentProperties properties) {
        return QueueBuilder.durable(properties.messaging().reservationQueue())
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }

    @Bean
    Queue paymentStayQueue(PaymentProperties properties) {
        return QueueBuilder.durable(properties.messaging().stayQueue())
                .deadLetterExchange(properties.messaging().deadLetterExchange())
                .deadLetterRoutingKey(properties.messaging().deadLetterRoutingKey())
                .build();
    }

    @Bean
    Binding paymentReservationBinding(
            Queue paymentReservationQueue,
            TopicExchange paymentEventExchange,
            PaymentProperties properties) {
        return BindingBuilder.bind(paymentReservationQueue)
                .to(paymentEventExchange)
                .with(properties.messaging().reservationRoutingKey());
    }

    @Bean
    Binding paymentStayBinding(
            Queue paymentStayQueue,
            TopicExchange paymentEventExchange,
            PaymentProperties properties) {
        return BindingBuilder.bind(paymentStayQueue)
                .to(paymentEventExchange)
                .with(properties.messaging().stayRoutingKey());
    }

    @Bean
    Queue paymentReservationDeadLetterQueue(PaymentProperties properties) {
        return QueueBuilder.durable(properties.messaging().deadLetterQueue()).build();
    }

    @Bean
    Binding paymentReservationDeadLetterBinding(
            Queue paymentReservationDeadLetterQueue,
            TopicExchange paymentDeadLetterExchange,
            PaymentProperties properties) {
        return BindingBuilder.bind(paymentReservationDeadLetterQueue)
                .to(paymentDeadLetterExchange)
                .with(properties.messaging().deadLetterRoutingKey());
    }
}
