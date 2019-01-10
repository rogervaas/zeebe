/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.processor.message;

import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;

public class OpenWorkflowInstanceSubscriptionProcessor
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  public static final String NO_SUB_FOUND_MSG =
      "Expected to open pre-existing workflow instance subscription with element %d and message %s, but none was found";
  public static final String NOT_OPENING_MSG =
      "Expected to complete opening workflow instance subscription with element %d and message %s, but it is already ";
  private final WorkflowInstanceSubscriptionState subscriptionState;

  public OpenWorkflowInstanceSubscriptionProcessor(
      final WorkflowInstanceSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter) {

    final WorkflowInstanceSubscriptionRecord subscriptionRecord = record.getValue();

    final WorkflowInstanceSubscription subscription =
        subscriptionState.getSubscription(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageName());
    if (subscription != null && subscription.isOpening()) {

      subscriptionState.updateToOpenedState(
          subscription, subscription.getSubscriptionPartitionId());

      streamWriter.appendFollowUpEvent(
          record.getKey(), WorkflowInstanceSubscriptionIntent.OPENED, subscriptionRecord);

    } else {
      final RejectionType type;
      final String reason;

      if (subscription == null) {
        type = RejectionType.NOT_FOUND;
        reason = NO_SUB_FOUND_MSG;
      } else {
        final String state = subscription.isClosing() ? "closing" : "opened";
        type = RejectionType.INVALID_STATE;
        reason = NOT_OPENING_MSG + state;
      }

      streamWriter.appendRejection(
          record,
          type,
          String.format(
              reason,
              subscriptionRecord.getElementInstanceKey(),
              BufferUtil.bufferAsString(subscriptionRecord.getMessageName())));
    }
  }
}
