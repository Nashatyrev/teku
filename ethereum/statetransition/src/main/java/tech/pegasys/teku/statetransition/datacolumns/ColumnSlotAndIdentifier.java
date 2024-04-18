package tech.pegasys.teku.statetransition.datacolumns;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public record ColumnSlotAndIdentifier(
    UInt64 slot,
    DataColumnIdentifier identifier
) {
}
