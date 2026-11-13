package com.hotelai.reservation.integration;

import com.hotelai.reservation.config.ReservationProperties;
import com.hotelai.reservation.config.CorrelationIdFilter;
import java.net.http.HttpClient;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class DomainClientConfiguration {
    @Bean
    @Qualifier("inventoryRestClient")
    RestClient inventoryRestClient(ReservationProperties properties) {
        return client(properties.clients().inventoryUrl(), properties);
    }

    @Bean
    @Qualifier("pricingRestClient")
    RestClient pricingRestClient(ReservationProperties properties) {
        return client(properties.clients().pricingUrl(), properties);
    }

    private RestClient client(String baseUrl, ReservationProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.clients().connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.clients().readTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    request.getHeaders().set(
                            CorrelationIdFilter.HEADER,
                            correlationId == null ? UUID.randomUUID().toString() : correlationId);
                    return execution.execute(request, body);
                })
                .build();
    }
}
