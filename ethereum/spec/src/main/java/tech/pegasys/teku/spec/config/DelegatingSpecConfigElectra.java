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

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.features.Eip7594;

import java.util.Optional;

public class DelegatingSpecConfigElectra extends DelegatingSpecConfigDeneb implements SpecConfigElectra {
  private final SpecConfigElectra specConfigElectra;
  // FIXME: why are we setting it in Electra if it's any fork feature?
  //  maybe say because it should be at least electra to set? Is it viable?
  private final Optional<Eip7594> eip7594;

  public DelegatingSpecConfigElectra(final SpecConfigElectra specConfig, final Optional<Eip7594> eip7594) {
    super(specConfig);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
    this.eip7594 = eip7594;
  }

  @Override
  public Optional<SpecConfigElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public Optional<Eip7594> getOptionalEip7594Config() {
    return eip7594;
  }

  public Bytes4 getElectraForkVersion() {
    return specConfigElectra.getElectraForkVersion();
  }

  public UInt64 getElectraForkEpoch() {
    return specConfigElectra.getElectraForkEpoch();
  }

  public UInt64 getMinPerEpochChurnLimitElectra() {
    return specConfigElectra.getMinPerEpochChurnLimitElectra();
  }

  public UInt64 getMinActivationBalance() {
    return specConfigElectra.getMinActivationBalance();
  }

  public UInt64 getMaxEffectiveBalanceElectra() {
    return specConfigElectra.getMaxEffectiveBalanceElectra();
  }

  public int getPendingBalanceDepositsLimit() {
    return specConfigElectra.getPendingBalanceDepositsLimit();
  }

  public int getPendingPartialWithdrawalsLimit() {
    return specConfigElectra.getPendingPartialWithdrawalsLimit();
  }

  public int getPendingConsolidationsLimit() {
    return specConfigElectra.getPendingConsolidationsLimit();
  }

  public int getMinSlashingPenaltyQuotientElectra() {
    return specConfigElectra.getMinSlashingPenaltyQuotientElectra();
  }

  public int getWhistleblowerRewardQuotientElectra() {
    return specConfigElectra.getWhistleblowerRewardQuotientElectra();
  }

  public int getMaxAttesterSlashingsElectra() {
    return specConfigElectra.getMaxAttesterSlashingsElectra();
  }

  public int getMaxAttestationsElectra() {
    return specConfigElectra.getMaxAttestationsElectra();
  }

  public int getMaxConsolidationRequestsPerPayload() {
    return specConfigElectra.getMaxConsolidationRequestsPerPayload();
  }

  public int getMaxDepositRequestsPerPayload() {
    return specConfigElectra.getMaxDepositRequestsPerPayload();
  }

  public int getMaxWithdrawalRequestsPerPayload() {
    return specConfigElectra.getMaxWithdrawalRequestsPerPayload();
  }

  public int getMaxPendingPartialsPerWithdrawalsSweep() {
    return specConfigElectra.getMaxPendingPartialsPerWithdrawalsSweep();
  }
}
