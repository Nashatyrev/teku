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

package tech.pegasys.teku.spec.config;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DelegatingSpecConfigFulu extends DelegatingSpecConfigElectra
    implements SpecConfigFulu {
  private final SpecConfigFulu specConfigFulu;

  public DelegatingSpecConfigFulu(final SpecConfigFulu specConfig) {
    super(specConfig);
    this.specConfigFulu = SpecConfigFulu.required(specConfig);
  }

  @Override
  public Optional<SpecConfigFulu> toVersionFulu() {
    return Optional.of(this);
  }

  @Override
  public Bytes4 getFuluForkVersion() {
    return specConfigFulu.getFuluForkVersion();
  }

  @Override
  public UInt64 getFuluForkEpoch() {
    return specConfigFulu.getFuluForkEpoch();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DelegatingSpecConfigFulu that = (DelegatingSpecConfigFulu) o;
    return Objects.equals(specConfigFulu, that.specConfigFulu);
  }

  @Override
  public int hashCode() {
    return Objects.hash(specConfigFulu);
  }
}
