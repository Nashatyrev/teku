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

package tech.pegasys.teku.spec.datastructures.blobs.versions.electra;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class Cell extends SszByteVectorImpl {

  Cell(final CellSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  Cell(final CellSchema cellSchema, final Bytes bytes) {
    super(cellSchema, bytes);
  }

  public String toBriefString() {
    return getBytes().slice(0, 7).toUnprefixedHexString();
  }
}
