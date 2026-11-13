package com.hotelai.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CassoSignatureTest {
    private static final String PUBLIC_SAMPLE_SECRET =
            "mgExMrjj4i7a1p8Cs6LmFbpCd8lbw4sSX4LZXPag7TKazeIogtR90tvWYTkwQbvs";
    private static final long PUBLIC_SAMPLE_TIMESTAMP = 1727948258788L;
    private static final String PUBLIC_SAMPLE_SIGNATURE =
            "ed0a4bd2e826d5cb69988cdb141e6c1a080e21f3b57eb72cd78192220042b9e7"
                    + "dde0868fc667faea8e224900fa7904e7c88dfa098032fb2d6b6996856e8b7ff3";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesOfficialCassoWebhookV2JavaSample() {
        String payload = """
                {
                  "error": 0,
                  "data": {
                    "id": 0,
                    "reference": "MA_GIAO_DICH_THU_NGHIEM",
                    "description": "giao dich thu nghiem",
                    "amount": 599000,
                    "runningBalance": 25000000,
                    "transactionDateTime": "2024-10-03 15:06:37",
                    "accountNumber": "88888888",
                    "bankName": "VPBank",
                    "bankAbbreviation": "VPB",
                    "virtualAccountNumber": "",
                    "virtualAccountName": "",
                    "counterAccountName": "NGUYEN VAN A",
                    "counterAccountNumber": "8888888888",
                    "counterAccountBankId": "970415",
                    "counterAccountBankName": "VietinBank"
                  }
                }
                """;

        assertThat(CassoSignature.sign(
                        objectMapper, payload, PUBLIC_SAMPLE_TIMESTAMP, PUBLIC_SAMPLE_SECRET))
                .isEqualTo(PUBLIC_SAMPLE_SIGNATURE);
        assertThat(CassoSignature.verify(
                        objectMapper,
                        payload,
                        "t=" + PUBLIC_SAMPLE_TIMESTAMP + ",v1=" + PUBLIC_SAMPLE_SIGNATURE,
                        PUBLIC_SAMPLE_SECRET))
                .isTrue();
    }

    @Test
    void rejectsTamperedPayload() {
        String payload = "{\"error\":0,\"data\":{\"id\":1,\"amount\":1000}}";
        String signature = CassoSignature.sign(
                objectMapper, payload, PUBLIC_SAMPLE_TIMESTAMP, PUBLIC_SAMPLE_SECRET);

        assertThat(CassoSignature.verify(
                        objectMapper,
                        payload.replace("1000", "9000"),
                        "t=" + PUBLIC_SAMPLE_TIMESTAMP + ",v1=" + signature,
                        PUBLIC_SAMPLE_SECRET))
                .isFalse();
    }
}
