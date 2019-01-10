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
package io.zeebe.broker.workflow.model.transformation.transformer;

import io.zeebe.broker.workflow.model.BpmnStep;
import io.zeebe.broker.workflow.model.element.ExecutableFlowNode;
import io.zeebe.broker.workflow.model.element.ExecutableSequenceFlow;
import io.zeebe.broker.workflow.model.element.ExecutableWorkflow;
import io.zeebe.broker.workflow.model.transformation.ModelElementTransformer;
import io.zeebe.broker.workflow.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.Event;
import io.zeebe.model.bpmn.instance.EventBasedGateway;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;

public class SequenceFlowTransformer implements ModelElementTransformer<SequenceFlow> {

  public static final String UNEXPECTED_TARGET_TYPE_MSG =
      "Unexpected sequence flow target of type %s; should be one of: Activity, Event, ExclusiveGateway, EventBaseGateway, or ParallelGateway";

  @Override
  public Class<SequenceFlow> getType() {
    return SequenceFlow.class;
  }

  @Override
  public void transform(final SequenceFlow element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableSequenceFlow sequenceFlow =
        workflow.getElementById(element.getId(), ExecutableSequenceFlow.class);

    compileCondition(element, sequenceFlow);
    connectWithFlowNodes(element, workflow, sequenceFlow);
    bindLifecycle(element, sequenceFlow);
  }

  private void bindLifecycle(
      final SequenceFlow element, final ExecutableSequenceFlow sequenceFlow) {
    final FlowNode target = element.getTarget();

    final BpmnStep step;

    if (target instanceof Activity) {
      step = BpmnStep.ENTER_FLOW_NODE;

    } else if (target instanceof Event) {
      step = BpmnStep.ENTER_EVENT;

    } else if (target instanceof ExclusiveGateway || target instanceof EventBasedGateway) {
      step = BpmnStep.ACTIVATE_GATEWAY;

    } else if (target instanceof ParallelGateway) {
      if (target.getIncoming().size() == 1) {
        step = BpmnStep.ACTIVATE_GATEWAY;
      } else {
        step = BpmnStep.PARALLEL_MERGE;
      }

    } else {
      throw new RuntimeException(
          String.format(UNEXPECTED_TARGET_TYPE_MSG, target.getClass().getSimpleName()));
    }

    sequenceFlow.bindLifecycleState(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, step);
  }

  private void connectWithFlowNodes(
      final SequenceFlow element,
      final ExecutableWorkflow workflow,
      final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode source =
        workflow.getElementById(element.getSource().getId(), ExecutableFlowNode.class);
    final ExecutableFlowNode target =
        workflow.getElementById(element.getTarget().getId(), ExecutableFlowNode.class);

    source.addOutgoing(sequenceFlow);
    target.addIncoming(sequenceFlow);
    sequenceFlow.setTarget(target);
  }

  private void compileCondition(
      final SequenceFlow element, final ExecutableSequenceFlow sequenceFlow) {
    final ConditionExpression conditionExpression = element.getConditionExpression();
    if (conditionExpression != null) {
      final String rawExpression = conditionExpression.getTextContent();
      final CompiledJsonCondition compiledExpression =
          JsonConditionFactory.createCondition(rawExpression);
      sequenceFlow.setCondition(compiledExpression);
    }
  }
}
