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

package tech.pegasys.teku.kzg;

<<<<<<<< HEAD:ethereum/spec/src/testFixtures/java/tech/pegasys/teku/spec/propertytest/suppliers/execution/versions/electra/WithdrawalRequestSupplier.java
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class WithdrawalRequestSupplier extends DataStructureUtilSupplier<WithdrawalRequest> {

  public WithdrawalRequestSupplier() {
    super(DataStructureUtil::randomWithdrawalRequest, SpecMilestone.ELECTRA);
========
import static ethereum.ckzg4844.CKZG4844JNI.BYTES_PER_CELL;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

public record KZGCell(Bytes bytes) {

  static final KZGCell ZERO = new KZGCell(Bytes.wrap(new byte[BYTES_PER_CELL]));

  static List<KZGCell> splitBytes(Bytes bytes) {
    return CKZG4844Utils.bytesChunked(bytes, BYTES_PER_CELL).stream().map(KZGCell::new).toList();
>>>>>>>> das:infrastructure/kzg/src/main/java/tech/pegasys/teku/kzg/KZGCell.java
  }
}
