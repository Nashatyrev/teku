package tech.pegasys.teku.spec.config.features;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface FeatureEip7594 {
    Bytes4 getEip7594ForkVersion();

    UInt64 getEip7594ForkEpoch();

    UInt64 getFieldElementsPerCell();

    UInt64 getFieldElementsPerExtBlob();

    /** DataColumnSidecar's */
    UInt64 getKzgCommitmentsInclusionProofDepth();

    int getNumberOfColumns();
}
