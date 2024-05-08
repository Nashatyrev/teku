package tech.pegasys.teku.statetransition.datacolumns.debug

import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDB
import java.util.*
import java.util.stream.Stream

class DasDBStub: DataColumnSidecarDB {

    val idsBySlot: TreeMap<UInt64, MutableMap<UInt64, DataColumnIdentifier>> = TreeMap()
    val sidecarsByColumnId: MutableMap<DataColumnIdentifier, DataColumnSidecar> = mutableMapOf()
    var firstIncompleteSlotHolder: Optional<UInt64> = Optional.empty()

    @Synchronized
    override fun getFirstIncompleteSlot(): Optional<UInt64> = firstIncompleteSlotHolder
    @Synchronized
    override fun getSidecar(identifier: DataColumnIdentifier?): Optional<DataColumnSidecar> =
        Optional.ofNullable(sidecarsByColumnId[identifier])

    @Synchronized
    override fun streamColumnIdentifiers(slot: UInt64): Stream<DataColumnIdentifier> =
        idsBySlot[slot]?.values?.stream() ?: Stream.empty()


    @Synchronized
    override fun setFirstIncompleteSlot(slot: UInt64) {
        firstIncompleteSlotHolder = Optional.of(slot)
    }

    @Synchronized
    override fun addSidecar(sidecar: DataColumnSidecar) {
        val identifier = sidecar.createIdentifier()
        sidecarsByColumnId[identifier] = sidecar
        idsBySlot.computeIfAbsent(sidecar.slot) { TreeMap() }[identifier.index] = identifier
    }

    @Synchronized
    override fun pruneAllSidecars(tillSlot: UInt64) {
        // skip
    }

    companion object {
        fun DataColumnSidecar.createIdentifier() = DataColumnIdentifier(this.blockRoot, this.index)
    }
}