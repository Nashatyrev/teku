package tech.pegasys.teku.spec.logic.common.util;

import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface CheckpointStateStore {

  BeaconState getState(Checkpoint checkpoint);

  public static class Tracking implements CheckpointStateStore {
    private final CheckpointStateStore delegate;
    private final List<Checkpoint> requestedCheckpoints = new ArrayList<>();

    public Tracking(CheckpointStateStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public BeaconState getState(Checkpoint checkpoint) {
      requestedCheckpoints.add(checkpoint);
      return delegate.getState(checkpoint);
    }

    public List<Checkpoint> getRequestedCheckpoints() {
      return requestedCheckpoints;
    }
  }

  public static class Caching implements CheckpointStateStore {
    private final CheckpointStateStore delegate;
    private final Map<Checkpoint, BeaconState> cache = new HashMap<>();

    public Caching(CheckpointStateStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public BeaconState getState(Checkpoint checkpoint) {
      return cache.computeIfAbsent(checkpoint, ch -> delegate.getState(ch));
    }
  }
}

