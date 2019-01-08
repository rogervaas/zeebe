package io.zeebe.client.cmd;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.zeebe.gateway.protocol.GatewayOuterClass.ErrorInfo;
import io.zeebe.gateway.protocol.GatewayOuterClass.RejectionInfo;

public class CommandException extends ClientException {

  private static final String MESSAGE_FORMAT = "%s : %s";
  private static final long serialVersionUID = -6093536690259731619L;
  private final Status status;

  public CommandException(Status status) {
    super(
        String.format(
            MESSAGE_FORMAT, Code.forNumber(status.getCode()).name(), status.getMessage()));
    this.status = status;
  }

  public CommandException(Status status, Throwable cause) {
    super(
        String.format(MESSAGE_FORMAT, Code.forNumber(status.getCode()).name(), status.getMessage()),
        cause);
    this.status = status;
  }

  public Status getStatus() {
    return status;
  }

  public String getDetails() {
    final StringBuilder builder = new StringBuilder();

    for (int i = 0; i < status.getDetailsCount(); i++) {
      final String detail = getDetail(i);
      if (detail != null) {
        builder.append(detail);
      }
    }
    return builder.toString();
  }

  public String getDetail(int index) {
    final Any info = status.getDetails(index);
    try {
      if (info.is(ErrorInfo.class)) {
        return info.unpack(ErrorInfo.class).toString();
      }

      if (info.is(RejectionInfo.class)) {
        return info.unpack(RejectionInfo.class).toString();
      }
    } catch (InvalidProtocolBufferException e) {
      // warn
    }

    return null;
  }
}
