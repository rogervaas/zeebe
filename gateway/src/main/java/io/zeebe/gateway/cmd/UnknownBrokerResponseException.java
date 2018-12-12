package io.zeebe.gateway.cmd;

import io.zeebe.gateway.impl.broker.response.BrokerResponse;

public class UnknownBrokerResponseException extends ClientException {
  private static final long serialVersionUID = 1L;

  public static final String ERROR_MESSAGE_FORMAT = "Unknown broker response (%s): %s%n";

  protected final BrokerResponse<?> response;

  public UnknownBrokerResponseException(BrokerResponse<?> response) {
    super(String.format(ERROR_MESSAGE_FORMAT, response));
    this.response = response;
  }

  public UnknownBrokerResponseException(BrokerResponse response, Throwable cause) {
    super(String.format(ERROR_MESSAGE_FORMAT, response), cause);
    this.response = response;
  }

  public BrokerResponse<?> getResponse() {
    return response;
  }
}
