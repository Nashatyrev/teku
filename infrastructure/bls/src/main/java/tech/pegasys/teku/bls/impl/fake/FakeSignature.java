/*
 * Copyright Consensys Software Inc., 2020
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

package tech.pegasys.teku.bls.impl.fake;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair;
import tech.pegasys.teku.bls.impl.Signature;

public class FakeSignature implements Signature {

  public static final FakeSignature ANY_SIGNATURE = new FakeSignature(false);
  public static final FakeSignature INF_SIGNATURE = new FakeSignature(true);

  private final boolean isInfinity;

  private FakeSignature(boolean isInfinity) {
    this.isInfinity = isInfinity;
  }

  @Override
  public Bytes toBytesCompressed() {
    return (isInfinity ? FakeBLS12381.INFINITY_SIGNATURE : FakeBLS12381.RANDOM_SIGNATURE)
        .toBytesCompressed();
  }

  @Override
  public boolean verify(List<PublicKeyMessagePair> keysToMessages) {
    return true;
  }

  @Override
  public boolean verify(PublicKey publicKey, Bytes message, String dst) {
    return true;
  }

  @Override
  public boolean isInfinity() {
    return isInfinity;
  }

  @Override
  public boolean isInGroup() {
    return true;
  }
}
