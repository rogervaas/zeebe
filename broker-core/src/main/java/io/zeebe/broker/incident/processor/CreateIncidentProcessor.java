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
package io.zeebe.broker.incident.processor;

import io.zeebe.broker.job.JobState;
import io.zeebe.broker.job.JobState.State;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.workflow.state.ElementInstanceState;
import io.zeebe.broker.workflow.state.IndexedRecord;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.intent.IncidentIntent;

public final class CreateIncidentProcessor implements CommandProcessor<IncidentRecord> {

  public static final String NO_FAILED_EVENT_MSG =
      "Expected to have a failed event %d, but none was found";
  public static final String NO_FAILED_JOB_MSG =
      "Expected job %d to be in failed state, but it was %s";

  private final ZeebeState zeebeState;

  public CreateIncidentProcessor(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
  }

  @Override
  public void onCommand(
      TypedRecord<IncidentRecord> command, CommandControl<IncidentRecord> commandControl) {
    final IncidentRecord incidentEvent = command.getValue();

    final boolean incidentIsNotRejected = !rejectIncidentCreation(incidentEvent, commandControl);

    if (incidentIsNotRejected) {
      final long incidentKey = commandControl.accept(IncidentIntent.CREATED, incidentEvent);
      zeebeState.getIncidentState().createIncident(incidentKey, incidentEvent);
    }
  }

  public boolean rejectIncidentCreation(
      IncidentRecord incidentEvent, CommandControl<IncidentRecord> commandControl) {
    final IncidentState incidentState = zeebeState.getIncidentState();

    final boolean isJobIncident = incidentState.isJobIncident(incidentEvent);

    if (isJobIncident) {
      return rejectJobIncident(incidentEvent.getJobKey(), commandControl);
    } else {
      return rejectWorkflowInstanceIncident(incidentEvent.getElementInstanceKey(), commandControl);
    }
  }

  private boolean rejectJobIncident(long jobKey, CommandControl<IncidentRecord> commandControl) {
    final JobState state = zeebeState.getJobState();
    final JobState.State jobState = state.getState(jobKey);

    if (jobState != State.FAILED) {
      RejectionType type = RejectionType.INVALID_STATE;
      String stateName = jobState.name();

      if (jobState == State.NOT_FOUND) {
        type = RejectionType.NOT_FOUND;
        stateName = "not found";
      }

      commandControl.reject(type, String.format(NO_FAILED_JOB_MSG, jobKey, stateName));
      return true;
    }

    return false;
  }

  private boolean rejectWorkflowInstanceIncident(
      long elementInstanceKey, CommandControl<IncidentRecord> commandControl) {
    final ElementInstanceState elementInstanceState =
        zeebeState.getWorkflowState().getElementInstanceState();

    final IndexedRecord failedToken = elementInstanceState.getFailedToken(elementInstanceKey);
    final boolean noFailedToken = failedToken == null;
    if (noFailedToken) {
      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NO_FAILED_EVENT_MSG, elementInstanceKey));
    }

    return noFailedToken;
  }
}
