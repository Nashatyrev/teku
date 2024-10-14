package tech.pegasys.teku.statetransition.datacolumns.log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;

public class DasGossipBatchLogger implements DasGossipLogger {
  private final Logger logger;
  private final TimeProvider timeProvider;

  private List<Event> events = new ArrayList<>();

  public DasGossipBatchLogger(
      AsyncRunner asyncRunner, TimeProvider timeProvider, Logger logger) {
    this.timeProvider = timeProvider;
    this.logger = logger;
    asyncRunner.runWithFixedDelay(
        this::sample,
        Duration.ofSeconds(1),
        err -> logger.debug("DasGossipBatchLogger error: " + err));
  }

  interface Event {
    long time();
  }

  record GossipReceiveEvent(long time, DataColumnSidecar sidecar) implements Event {}

  record GossipValidateEvent(
      long time, DataColumnSidecar sidecar, InternalValidationResult validationResult)
      implements Event {}

  private void sample() {
    final List<Event> eventsLoc;
    synchronized (this) {
      if (events.isEmpty()) {
        return;
      }
      eventsLoc = events;
      events = new ArrayList<>();
    }

    Map<SlotAndBlockRoot, List<GossipValidateEvent>> eventsByBlock = new TreeMap<>();
    for (Event event : eventsLoc) {
      if (event instanceof GossipValidateEvent e) {
        DataColumnSidecar sidecar = e.sidecar();
        SlotAndBlockRoot blockId = new SlotAndBlockRoot(sidecar.getSlot(), sidecar.getBlockRoot());
        eventsByBlock.computeIfAbsent(blockId, __ -> new ArrayList<>()).add(e);
      }
    }
    eventsByBlock.forEach(this::logGossipForBlock);
  }

  private void logGossipForBlock(SlotAndBlockRoot blockId, List<GossipValidateEvent> events) {
    long curTime = timeProvider.getTimeInMillis().longValue();
    Set<ValidationResultCode> validationCodes =
        events.stream().map(e -> e.validationResult().code()).collect(Collectors.toSet());
    if (validationCodes.size() == 1) {
      ValidationResultCode validationCode = events.getFirst().validationResult().code();
      List<Integer> columnIndexes =
          events.stream().map(e -> e.sidecar().getIndex().intValue()).toList();
      long firstMillisAgo = curTime - events.getFirst().time();
      long lastMillisAgo = curTime - events.getLast().time();
      String timeStr = " (" + lastMillisAgo + " - " + firstMillisAgo + ") ms ago";
      String blockStr =
          blockId.getSlot()
              + " ("
              + LogFormatter.formatAbbreviatedHashRoot(blockId.getBlockRoot())
              + ")";
      String s =
          "Received "
              + events.size()
              + " data columns (validation result: " + validationCode + ") by gossip "
              + timeStr
              + " for block "
              + blockStr
              + ": "
              + StringifyUtil.toIntRangeString(columnIndexes);
      logger.debug(s);
    } else {
      events.forEach(
          e -> {
            String timeStr = " " + (curTime - e.time()) + " ms ago";
            String s =
                "Received "
                    + e.validationResult().code()
                    + " data column by gossip "
                    + timeStr
                    + ": "
                    + DataColumnSlotAndIdentifier.fromDataColumn(e.sidecar());
          });
    }
  }

  @Override
  public synchronized void onGossipInboundReceive(DataColumnSidecar sidecar) {
    events.add(new GossipReceiveEvent(timeProvider.getTimeInMillis().longValue(), sidecar));
  }

  @Override
  public synchronized void onGossipInboundValidate(
      DataColumnSidecar sidecar, InternalValidationResult validationResult) {
    events.add(
        new GossipValidateEvent(
            timeProvider.getTimeInMillis().longValue(), sidecar, validationResult));
  }

  @Override
  public synchronized void onGossipOutboundPublish(DataColumnSidecar sidecar) {}

  @Override
  public void onGossipOutboundPublishError(DataColumnSidecar sidecar, Throwable err) {

  }
}
