package tech.pegasys.teku.statetransition.datacolumns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class SimpleDataAvailabilitySampler implements DataAvailabilitySampler {

  private final DataColumnSidecarRetriever retriever;
  private final Random random;
  private final int samplesPerSlot;
  private final int extBlobColumnCount;

  public SimpleDataAvailabilitySampler(
      DataColumnSidecarRetriever retriever,
      Random random,
      int samplesPerSlot,
      int extBlobColumnCount) {
    this.retriever = retriever;
    this.random = random;
    this.samplesPerSlot = samplesPerSlot;
    this.extBlobColumnCount = extBlobColumnCount;
  }

  private Collection<UInt64> getColumnsForSampling() {
    List<UInt64> allColumnIndexes =
        Stream.iterate(0, (idx) -> idx < extBlobColumnCount, (idx) -> idx + 1)
            .map(UInt64::valueOf)
            .collect(Collectors.toList());;

    List<UInt64> selectedIndexes = new ArrayList<>();
    for(int i = 0; i < samplesPerSlot; i++) {
      int idx = random.nextInt(allColumnIndexes.size());
      UInt64 columnIndex = allColumnIndexes.remove(idx);
      selectedIndexes.add(columnIndex);
    }
    return selectedIndexes;
  }

  @Override
  public SafeFuture<Void> checkDataAvailability(UInt64 slot, Bytes32 blockRoot) {

    Collection<UInt64> columnsForSampling = getColumnsForSampling();

    Stream<SafeFuture<?>> samplingPromises =
        columnsForSampling.stream()
            .map(
                columnIndex ->
                    retriever.retrieve(
                        new ColumnSlotAndIdentifier(
                            slot, new DataColumnIdentifier(blockRoot, columnIndex))));

    return SafeFuture.allOf(samplingPromises);
  }
}
