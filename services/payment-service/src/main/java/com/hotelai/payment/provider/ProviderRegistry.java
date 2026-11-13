package com.hotelai.payment.provider;

import com.hotelai.payment.common.ApiException;
import com.hotelai.payment.domain.PaymentProviderCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProviderRegistry {
    private final Map<PaymentProviderCode, PaymentProvider> providers;

    public ProviderRegistry(List<PaymentProvider> adapters) {
        Map<PaymentProviderCode, PaymentProvider> values = new EnumMap<>(PaymentProviderCode.class);
        adapters.forEach(adapter -> {
            if (values.put(adapter.code(), adapter) != null) {
                throw new IllegalStateException("Duplicate payment provider adapter " + adapter.code());
            }
        });
        this.providers = Map.copyOf(values);
    }

    public PaymentProvider required(PaymentProviderCode code) {
        PaymentProvider provider = providers.get(code);
        if (provider == null) {
            throw ApiException.unavailable("provider-unavailable", "Payment provider is not configured: " + code);
        }
        return provider;
    }
}
