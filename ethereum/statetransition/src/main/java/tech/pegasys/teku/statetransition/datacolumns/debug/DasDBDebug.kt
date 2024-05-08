package tech.pegasys.teku.statetransition.datacolumns.debug

import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarDB
import java.util.TreeMap

class DasDBDebug(val delegate: DataColumnSidecarDB) : DataColumnSidecarDB by delegate {

    val slotToColumns: TreeMap<UInt64, MutableSet<UInt64>> = TreeMap()

    fun collectInitialInfo(currentSlot: UInt64) {
        generateSequence(UInt64.ZERO) { it.increment() }
            .takeWhile { it.isLessThanOrEqualTo(currentSlot) }
            .forEach { slot ->
                val columnsSet = streamColumnIdentifiers(slot)
                    .map { it.index }
                    .toList()
                    .toMutableSet()
                if (columnsSet.isNotEmpty()) {
                    slotToColumns[slot] = columnsSet
                }
            }
    }

    @Synchronized
    override fun addSidecar(sidecar: DataColumnSidecar) {
        slotToColumns.computeIfAbsent(sidecar.slot) { mutableSetOf() } += sidecar.index
        delegate.addSidecar(sidecar)
    }

    @Synchronized
    fun createDigest(): String {
        return when {
            slotToColumns.isEmpty() -> "<empty>"
            else -> {
                val totalColumnCount = slotToColumns.values.sumOf { it.size }
                val firstEntry = slotToColumns.firstEntry()!!
                val lastEntry = slotToColumns.lastEntry()!!
                "Total: $totalColumnCount, firstIncomplete: $firstIncompleteSlot, ${firstEntry.key}(${firstEntry.value.size}) - ${lastEntry.key}(${lastEntry.value.size})"
            }
        }
    }
}