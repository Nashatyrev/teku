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

import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.PublicKey;

public class FakePublicKey implements PublicKey {

  private final PublicKey delegate;

  public static PublicKey create(PublicKey delegate) {
    if (delegate instanceof FakePublicKey) {
      return delegate;
    } else {
      return new FakePublicKey(delegate);
    }
  }

  private FakePublicKey(PublicKey delegate) {
    this.delegate = delegate;
  }

  @Override
  public Bytes48 toBytesCompressed() {
    return delegate.toBytesCompressed();
  }

  @Override
  public void forceValidation() throws IllegalArgumentException {}

  @Override
  public boolean isInGroup() {
    return true;
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
