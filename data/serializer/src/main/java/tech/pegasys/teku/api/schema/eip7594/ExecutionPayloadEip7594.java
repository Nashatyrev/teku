/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.api.schema.eip7594;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.api.schema.ExecutionPayload;
import tech.pegasys.teku.api.schema.capella.Withdrawal;
import tech.pegasys.teku.api.schema.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadEip7594 extends ExecutionPayloadDeneb implements ExecutionPayload {

  @JsonCreator
  public ExecutionPayloadEip7594(
      @JsonProperty("parent_hash") final Bytes32 parentHash,
      @JsonProperty("fee_recipient") final Bytes20 feeRecipient,
      @JsonProperty("state_root") final Bytes32 stateRoot,
      @JsonProperty("receipts_root") final Bytes32 receiptsRoot,
      @JsonProperty("logs_bloom") final Bytes logsBloom,
      @JsonProperty("prev_randao") final Bytes32 prevRandao,
      @JsonProperty("block_number") final UInt64 blockNumber,
      @JsonProperty("gas_limit") final UInt64 gasLimit,
      @JsonProperty("gas_used") final UInt64 gasUsed,
      @JsonProperty("timestamp") final UInt64 timestamp,
      @JsonProperty("extra_data") final Bytes extraData,
      @JsonProperty("base_fee_per_gas") final UInt256 baseFeePerGas,
      @JsonProperty("block_hash") final Bytes32 blockHash,
      @JsonProperty("transactions") final List<Bytes> transactions,
      @JsonProperty("withdrawals") final List<Withdrawal> withdrawals,
      @JsonProperty("blob_gas_used") final UInt64 blobGasUsed,
      @JsonProperty("excess_blob_gas") final UInt64 excessBlobGas) {
    super(
        parentHash,
        feeRecipient,
        stateRoot,
        receiptsRoot,
        logsBloom,
        prevRandao,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        blockHash,
        transactions,
        withdrawals,
        blobGasUsed,
        excessBlobGas);
  }

  public ExecutionPayloadEip7594(
      final tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    super(executionPayload);
  }

  @Override
  public Optional<ExecutionPayloadEip7594> toVersionEip7594() {
    return Optional.of(this);
  }
}