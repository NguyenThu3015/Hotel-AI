package com.hotelai.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundRequestTest {
    @Test
    void requesterCannotApproveOwnRefund() {
        UUID requester = UUID.randomUUID();
        RefundRequest refund =
                new RefundRequest(UUID.randomUUID(), new BigDecimal("100000"), "VND", "Cancellation", requester);

        assertThatThrownBy(() -> refund.approve(requester))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot approve");
    }

    @Test
    void approvedRefundMovesThroughProcessingToCompletedIdempotently() {
        RefundRequest refund =
                new RefundRequest(
                        UUID.randomUUID(),
                        new BigDecimal("100000"),
                        "VND",
                        "Cancellation",
                        UUID.randomUUID());
        refund.approve(UUID.randomUUID());
        refund.processing("provider-refund-1", "{\"accepted\":true}");

        assertThat(refund.complete("provider-refund-1", "{\"done\":true}")).isTrue();
        assertThat(refund.complete("provider-refund-1", "{\"done\":true}")).isFalse();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }
}
