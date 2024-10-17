/*
 * Copyright Consensys Software Inc., 2024
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

abstract class AbstractResponseLogger<TRequest, TResponse, TResponseSummary>
    implements ReqRespResponseLogger<TResponse> {
  protected static final Logger LOG = LogManager.getLogger(DasReqRespLogger.class);

  enum Direction {
    INBOUND,
    OUTBOUND;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  protected record Timestamped<T>(long time, T value) {}

  protected final TimeProvider timeProvider;
  protected final Direction direction;
  protected final LoggingPeerId peerId;
  protected final TRequest request;
  private final Function<TResponse, TResponseSummary> responseSummarizer;
  protected final long requestTime;
  private final Logger logger;
  private final Level logLevel;

  private final List<Timestamped<TResponseSummary>> responseSummaries = new ArrayList<>();
  private volatile boolean done = false;

  public AbstractResponseLogger(
      TimeProvider timeProvider,
      Direction direction,
      LoggingPeerId peerId,
      TRequest request,
      Function<TResponse, TResponseSummary> responseSummarizer,
      Logger logger,
      Level logLevel) {
    this.timeProvider = timeProvider;
    this.direction = direction;
    this.peerId = peerId;
    this.request = request;
    this.responseSummarizer = responseSummarizer;
    this.requestTime = timeProvider.getTimeInMillis().longValue();
    this.logger = logger;
    this.logLevel = logLevel;
  }

  protected Logger getLogger() {
    return logger;
  }

  protected Level getLogLevel() {
    return logLevel;
  }

  protected abstract void responseComplete(
      List<Timestamped<TResponseSummary>> responseSummaries, Optional<Throwable> result);

  protected void reportExtraEventAfterDone(String eventDescr) {
    getLogger().log(logLevel, "ERROR: extra event after response done: " + eventDescr);
  }

  @Override
  public synchronized void onNextItem(TResponse responseItem) {
    TResponseSummary responseSummary = responseSummarizer.apply(responseItem);
    if (done) {
      getLogger().debug("onNextItem: " + responseSummary);
      return;
    }
    responseSummaries.add(
        new Timestamped<>(timeProvider.getTimeInMillis().longValue(), responseSummary));
  }

  @Override
  public void onComplete() {
    if (done) {
      getLogger().debug("onComplete");
      return;
    }
    finalize(Optional.empty());
  }

  @Override
  public void onError(Throwable error) {
    if (done) {
      getLogger().debug("onError: " + error);
      return;
    }
    finalize(Optional.ofNullable(error));
  }

  private void finalize(Optional<Throwable> result) {
    done = true;
    responseComplete(responseSummaries, result);
  }
}
