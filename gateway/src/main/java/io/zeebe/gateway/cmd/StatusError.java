package io.zeebe.gateway.cmd;

import com.google.rpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

/**
 * A status error can map itself to a gRPC standard Status {@link com.google.rpc.Status} which will
 * be sent back to the client by mapping it using {@link
 * StatusProto#toStatusRuntimeException(Status)}}
 */
public interface StatusError {
  Status toStatus();

  default StatusRuntimeException toStatusRuntimeException() {
    return StatusProto.toStatusRuntimeException(toStatus());
  }
}
