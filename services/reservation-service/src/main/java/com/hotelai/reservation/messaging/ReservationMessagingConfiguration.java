package com.hotelai.reservation.messaging;

import com.hotelai.reservation.config.ReservationProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ReservationMessagingConfiguration {
    @Bean
    TopicExchange reservationEventExchange(ReservationProperties properties) {
        return new TopicExchange(properties.messaging().exchange(), true, false);
    }

    @Bean
    TopicExchange reservationDeadLetterExchange(ReservationProperties properties) {
        return new TopicExchange(properties.messaging().exchange() + ".dlx", true, false);
    }

    @Bean
    Queue reservationPaymentQueue(ReservationProperties properties) {
        String name = properties.messaging().paymentQueue();
        return QueueBuilder.durable(name)
                .deadLetterExchange(properties.messaging().exchange() + ".dlx")
                .deadLetterRoutingKey(name + ".dead")
                .build();
    }

    @Bean
    Binding reservationPaymentBinding(
            Queue reservationPaymentQueue,
            TopicExchange reservationEventExchange,
            ReservationProperties properties) {
        return BindingBuilder.bind(reservationPaymentQueue)
                .to(reservationEventExchange)
                .with(properties.messaging().paymentRoutingKey());
    }

    @Bean
    Queue reservationPaymentDeadLetterQueue(ReservationProperties properties) {
        return QueueBuilder.durable(properties.messaging().paymentQueue() + ".dlq").build();
    }

    @Bean
    Binding reservationPaymentDeadLetterBinding(
            Queue reservationPaymentDeadLetterQueue,
            TopicExchange reservationDeadLetterExchange,
            ReservationProperties properties) {
        return BindingBuilder.bind(reservationPaymentDeadLetterQueue)
                .to(reservationDeadLetterExchange)
                .with(properties.messaging().paymentQueue() + ".dead");
    }

    @Bean
    Queue reservationStayQueue(ReservationProperties properties) {
        String name = properties.messaging().stayQueue();
        return QueueBuilder.durable(name)
                .deadLetterExchange(properties.messaging().exchange() + ".dlx")
                .deadLetterRoutingKey(name + ".dead")
                .build();
    }

    @Bean
    Declarables reservationStayBindings(
            Queue reservationStayQueue,
            TopicExchange reservationEventExchange,
            ReservationProperties properties) {
        return new Declarables(properties.messaging().stayRoutingKeys().stream()
                .map(routingKey -> BindingBuilder.bind(reservationStayQueue)
                        .to(reservationEventExchange)
                        .with(routingKey))
                .toList());
    }

    @Bean
    Queue reservationStayDeadLetterQueue(ReservationProperties properties) {
        return QueueBuilder.durable(properties.messaging().stayQueue() + ".dlq").build();
    }

    @Bean
    Binding reservationStayDeadLetterBinding(
            Queue reservationStayDeadLetterQueue,
            TopicExchange reservationDeadLetterExchange,
            ReservationProperties properties) {
        return BindingBuilder.bind(reservationStayDeadLetterQueue)
                .to(reservationDeadLetterExchange)
                .with(properties.messaging().stayQueue() + ".dead");
    }
}
