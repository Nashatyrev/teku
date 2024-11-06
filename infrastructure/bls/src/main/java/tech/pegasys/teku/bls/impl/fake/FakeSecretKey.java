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

package tech.pegasys.teku.bls.impl.fake;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;

public class FakeSecretKey implements SecretKey {

  private final SecretKey delegate;

  public FakeSecretKey(SecretKey delegate) {
    this.delegate = delegate;
  }

  @Override
  public Bytes32 toBytes() {
    return delegate.toBytes();
  }

  @Override
  public PublicKey derivePublicKey() {
    return FakePublicKey.create(delegate.derivePublicKey());
  }

  @Override
  public Signature sign(Bytes message) {
    return FakeSignature.ANY_SIGNATURE;
  }

  @Override
  public Signature sign(Bytes message, String dst) {
    return FakeSignature.ANY_SIGNATURE;
  }

  @Override
  public void destroy() {}
}
