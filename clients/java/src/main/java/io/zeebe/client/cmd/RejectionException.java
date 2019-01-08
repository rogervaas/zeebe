package io.zeebe.client.cmd;

import io.zeebe.gateway.protocol.GatewayOuterClass.RejectionError;

public class RejectionException extends ClientException {
  private static final long serialVersionUID = -910732317451208179L;

  private final RejectionError error;

  public RejectionException(RejectionError error) {
    super(String.format("Command %s rejected as %s", error.getIntent(), error.getType().name()));
    this.error = error;
  }

  public String getReason() {
    return this.error.getReason();
  }

  @Override
  public String toString() {
    return getMessage() + " : " + getReason();
  }
}
