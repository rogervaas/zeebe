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
package io.zeebe.gateway.impl.broker.response;

import io.zeebe.gateway.protocol.GatewayOuterClass.ErrorInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.ErrorInfo.Code;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.impl.encoding.ErrorResponse;
import io.zeebe.util.buffer.BufferUtil;

public class BrokerError {

  private final ErrorCode code;
  private final String message;

  public BrokerError(ErrorResponse errorResponse) {
    this(errorResponse.getErrorCode(), BufferUtil.bufferAsString(errorResponse.getErrorData()));
  }

  public BrokerError(ErrorCode code, String message) {
    this.code = code;
    this.message = message;
  }

  public ErrorCode getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "BrokerError{" + "code=" + code + ", message='" + message + '\'' + '}';
  }

  public ErrorInfo toErrorInfo() {
    return ErrorInfo.newBuilder().setCode(mapErrorCode()).setMessage(message).build();
  }

  private Code mapErrorCode() {
    switch (code) {
      case INVALID_MESSAGE: return Code.INVALID_MESSAGE;
      case NOT_FOUND: return Code.NOT_FOUND;
      case REQUEST_TIMEOUT: return Code.REQUEST_TIMEOUT;
      case PARTITION_NOT_FOUND: return Code.PARTITION_NOT_FOUND;
      case MESSAGE_NOT_SUPPORTED: return Code.MESSAGE_NOT_SUPPORTED;
      case REQUEST_WRITE_FAILURE: return Code.REQUEST_WRITE_FAILURE;
      case INVALID_CLIENT_VERSION: return Code.INVALID_CLIENT_VERSION;
      case REQUEST_PROCESSING_FAILURE: return Code.REQUEST_PROCESSING_FAILURE;
      default:
        return Code.UNRECOGNIZED;
    }
  }
}
