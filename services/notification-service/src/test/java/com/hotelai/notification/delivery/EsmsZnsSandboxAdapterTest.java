package com.hotelai.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hotelai.notification.domain.NotificationChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EsmsZnsSandboxAdapterTest {
    private static final String ENDPOINT =
            "https://rest.esms.vn/MainService.svc/json/SendZaloMessage_V5_post/";
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void sendsExpectedSandboxPayloadAndReturnsProviderReference() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "ApiKey":"api-key",
                          "SecretKey":"secret-key",
                          "OAID":"4097311281936189049",
                          "Phone":"84912345678",
                          "TempData":[{"key":"otp","value":"686868"}],
                          "TempID":"205644",
                          "Sandbox":"1"
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"100\",\"SMSID\":\"sandbox-message-id\"}",
                        MediaType.APPLICATION_JSON));

        EsmsZnsSandboxAdapter adapter = adapter();
        DeliveryAdapter.DeliveryResult result = adapter.send(
                new DeliveryAdapter.DeliveryRequest(
                        "0912 345 678", null, "686868", "correlation-id"));

        assertThat(adapter.channel()).isEqualTo(NotificationChannel.ZALO);
        assertThat(result.providerReference()).isEqualTo("sandbox-message-id");
        server.verify();
    }

    @Test
    void rejectsProviderErrorWithoutExposingCredentials() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"789\",\"ErrorMessage\":\"TemplateId is not config\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter().send(
                        new DeliveryAdapter.DeliveryRequest(
                                "+84912345678", null, "686868", "correlation-id")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("789")
                .hasMessageContaining("TemplateId is not config")
                .hasMessageNotContaining("api-key")
                .hasMessageNotContaining("secret-key");
        server.verify();
    }

    @Test
    void convertsHttpErrorsToSafeDeliveryFailure() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter().send(
                        new DeliveryAdapter.DeliveryRequest(
                                "84912345678", null, "686868", "correlation-id")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("eSMS ZNS HTTP request failed with status 503")
                .hasMessageNotContaining("api-key");
        server.verify();
    }

    @Test
    void normalizesSupportedVietnamesePhoneFormats() {
        assertThat(EsmsZnsSandboxAdapter.normalizeVietnamesePhone("0912.345.678"))
                .isEqualTo("84912345678");
        assertThat(EsmsZnsSandboxAdapter.normalizeVietnamesePhone("+84912345678"))
                .isEqualTo("84912345678");
        assertThat(EsmsZnsSandboxAdapter.normalizeVietnamesePhone("84912345678"))
                .isEqualTo("84912345678");
    }

    @Test
    void rejectsInvalidPhoneAndNonSandboxConfiguration() {
        assertThatThrownBy(() -> EsmsZnsSandboxAdapter.normalizeVietnamesePhone("123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Vietnamese mobile number");

        assertThatThrownBy(() -> new EsmsZnsSandboxAdapter(
                        builder, ENDPOINT, "api-key", "secret-key",
                        "4097311281936189049", "205644", "otp", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ZALO_SANDBOX must be true");
    }

    private EsmsZnsSandboxAdapter adapter() {
        return new EsmsZnsSandboxAdapter(
                builder, ENDPOINT, "api-key", "secret-key",
                "4097311281936189049", "205644", "otp", true);
    }
}
