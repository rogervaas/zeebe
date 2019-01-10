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
package io.zeebe.broker.workflow.processor.sequenceflow;

import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.BpmnStepHandler;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class StartFlowNodeHandler implements BpmnStepHandler<ExecutableSequenceFlow> {

  private final WorkflowInstanceIntent nodeIntent;
  private final BpmnElementType elementType;

  public StartFlowNodeHandler(WorkflowInstanceIntent nodeIntent, BpmnElementType bpmnElementType) {
    this.nodeIntent = nodeIntent;
    this.elementType = bpmnElementType;
  }

  @Override
  public void handle(BpmnStepContext<ExecutableSequenceFlow> context) {
    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode targetNode = sequenceFlow.getTarget();

    final WorkflowInstanceRecord value = context.getValue();
    value.setElementId(targetNode.getId());
    value.setBpmnElementType(elementType);

    context.getOutput().appendNewEvent(nodeIntent, value);
  }
}
