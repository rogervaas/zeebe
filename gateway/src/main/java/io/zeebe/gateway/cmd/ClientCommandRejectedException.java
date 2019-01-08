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
import io.zeebe.gateway.impl.broker.response.BrokerRejection;

/** A client command was rejected by the broker. */
public class ClientCommandRejectedException extends ClientException implements StatusError {
  private static final long serialVersionUID = 1L;

  private final BrokerRejection rejection;

  public ClientCommandRejectedException(BrokerRejection rejection) {
    super(rejection.getMessage());
    this.rejection = rejection;
  }

  public ClientCommandRejectedException(BrokerRejection rejection, Throwable cause) {
    super(rejection.getMessage(), cause);
    this.rejection = rejection;
  }

  @Override
  public Status toStatus() {
    return Status.newBuilder()
        .setCode(getStatusCode().getNumber())
        .setMessage(getMessage())
        .addDetails(Any.pack(rejection.toRejectionInfo()))
        .build();
  }

  private Code getStatusCode() {
    switch (rejection.getType()) {
      case BAD_VALUE:
        return Code.INVALID_ARGUMENT;
      case NOT_APPLICABLE:
        return Code.FAILED_PRECONDITION;
      case PROCESSING_ERROR:
        return Code.INTERNAL;
      default:
        return Code.UNKNOWN;
    }
  }
}
