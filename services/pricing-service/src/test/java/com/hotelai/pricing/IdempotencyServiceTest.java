package com.hotelai.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IdempotencyServiceTest {
  private final IdempotencyRecordRepository records = mock(IdempotencyRecordRepository.class);
  private final ObjectMapper mapper = mock(ObjectMapper.class);
  private final IdempotencyService service = new IdempotencyService(records, mapper);

  @Test
  void newCommandStoresResponseAndReplayDoesNotRunActionAgain() throws Exception {
    TestRequest request = new TestRequest("same");
    TestResponse response = new TestResponse("created");
    when(mapper.writeValueAsString(request)).thenReturn("{\"value\":\"same\"}");
    when(mapper.writeValueAsString(response)).thenReturn("{\"result\":\"created\"}");
    when(records.findByOperationAndIdempotencyKey("operation", "key-1"))
        .thenReturn(Optional.empty());
    AtomicInteger executions = new AtomicInteger();

    TestResponse first =
        service.execute(
            "key-1",
            "operation",
            request,
            TestResponse.class,
            () -> {
              executions.incrementAndGet();
              return response;
            });

    assertThat(first).isEqualTo(response);
    assertThat(executions).hasValue(1);
    verify(records).save(any(IdempotencyRecord.class));
  }

  @Test
  void reusingKeyWithDifferentPayloadIsRejected() throws Exception {
    TestRequest original = new TestRequest("one");
    TestRequest changed = new TestRequest("two");
    when(mapper.writeValueAsString(original)).thenReturn("one");
    when(mapper.writeValueAsString(changed)).thenReturn("two");
    String originalHash = service.requestHash(original);
    IdempotencyRecord existing = new IdempotencyRecord("operation", "key-1", originalHash, "{}");
    when(records.findByOperationAndIdempotencyKey("operation", "key-1"))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.execute(
                    "key-1", "operation", changed, TestResponse.class, () -> new TestResponse("x")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("different request");
    verify(records, never()).save(any());
  }

  private record TestRequest(String value) {}

  private record TestResponse(String result) {}
}
