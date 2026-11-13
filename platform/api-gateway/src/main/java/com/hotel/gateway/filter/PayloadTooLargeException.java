package com.hotel.gateway.filter;

public class PayloadTooLargeException extends RuntimeException {

  private final long maximumBytes;

  public PayloadTooLargeException(long maximumBytes) {
    super("Request body exceeds the configured limit of " + maximumBytes + " bytes");
    this.maximumBytes = maximumBytes;
  }

  public long getMaximumBytes() {
    return maximumBytes;
  }
}
