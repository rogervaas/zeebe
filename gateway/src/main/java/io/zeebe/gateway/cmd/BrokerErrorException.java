/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.cmd;

import com.google.protobuf.Any;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.zeebe.gateway.impl.broker.response.BrokerError;
import io.zeebe.protocol.clientapi.ErrorCode;

public class BrokerErrorException extends ClientException implements StatusError {
  private static final long serialVersionUID = 1L;

  public static final String ERROR_MESSAGE_FORMAT = "Request exception (%s): %s%n";

  private final BrokerError error;

  public BrokerErrorException(BrokerError error) {
    super(String.format(ERROR_MESSAGE_FORMAT, error.getCode(), error.getMessage()));
    this.error = error;
  }

  public ErrorCode getErrorCode() {
    return error.getCode();
  }

  public String getErrorMessage() {
    return error.getMessage();
  }

  @Override
  public Status toStatus() {
    return Status.newBuilder()
        .setCode(getStatusCode().getNumber())
        .setMessage(getMessage())
        .setDetails(0, Any.pack(error.toErrorInfo()))
        .build();
  }

  private Code getStatusCode() {
    switch (error.getCode()) {
      case REQUEST_PROCESSING_FAILURE:
    }
  }
}
