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
package io.zeebe.broker.job;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.incident.processor.IncidentState;
import io.zeebe.broker.job.JobState.State;
import io.zeebe.broker.logstreams.processor.CommandProcessor;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.JobIntent;
import org.slf4j.Logger;

public class CompleteProcessor implements CommandProcessor<JobRecord> {
  public static final String NO_JOB_FOUND_MSG =
      "Expected to complete job %d, but no such job exists";
  public static final String NO_FAILED_JOB_MSG =
      "Expected to complete job %d, but the job is failed; incident %d must be resolved first";
  public static final String CORRUPT_STATE_MSG =
      "Expected to complete job %d, but the job is failed; no incident was created, an internal error was raised";
  private static final Logger LOG = Loggers.WORKFLOW_PROCESSOR_LOGGER;

  private final JobState state;
  private final IncidentState incidentState;

  public CompleteProcessor(JobState state, IncidentState incidentState) {
    this.state = state;
    this.incidentState = incidentState;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {

    final long jobKey = command.getKey();

    if (state.exists(jobKey)) {
      if (!state.isInState(jobKey, State.FAILED)) {
        final JobRecord job = state.getJob(jobKey);
        job.setPayload(command.getValue().getPayload());

        state.delete(jobKey, job);
        commandControl.accept(JobIntent.COMPLETED, job);
      } else {
        final long incidentKey = incidentState.getJobIncidentKey(jobKey);
        if (incidentKey == IncidentState.MISSING_INCIDENT) {
          final String errorMessage = String.format(CORRUPT_STATE_MSG, jobKey);
          LOG.error(errorMessage);

          commandControl.reject(RejectionType.INVALID_STATE, errorMessage);
        } else {
          commandControl.reject(
              RejectionType.INVALID_STATE, String.format(NO_FAILED_JOB_MSG, jobKey, incidentKey));
        }
      }
    } else {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MSG, jobKey));
    }
  }
}
