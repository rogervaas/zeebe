package io.zeebe.client.cmd;

import io.zeebe.gateway.protocol.GatewayOuterClass.BrokerError;

public class BrokerException extends ClientException {
  private static final long serialVersionUID = 1483656742546651926L;
  private final BrokerError error;

  public BrokerException(BrokerError error) {
    super(String.format("%s - %s", error.getCode().name(), error.getMessage()));
    this.error = error;
  }

  public BrokerError getError() {
    return error;
  }
}
