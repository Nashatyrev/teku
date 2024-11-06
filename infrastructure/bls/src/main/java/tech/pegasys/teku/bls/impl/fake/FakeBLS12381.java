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

import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.bls.impl.KeyPair;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;

public class FakeBLS12381 implements BLS12381 {
  private static final BLS12381 DELEGATE = BlstLoader.INSTANCE.get();
  private static final BatchSemiAggregate FAKE_AGGREGATE = new BatchSemiAggregate() {};

  static final Signature RANDOM_SIGNATURE = DELEGATE.randomSignature(0);
  static final Signature INFINITY_SIGNATURE = DELEGATE.aggregateSignatures(Collections.emptyList());

  @Override
  public KeyPair generateKeyPair(Random random) {
    KeyPair keyPair = DELEGATE.generateKeyPair(random);
    return new KeyPair(
        new FakeSecretKey(keyPair.getSecretKey()), FakePublicKey.create(keyPair.getPublicKey()));
  }

  @Override
  public PublicKey publicKeyFromCompressed(Bytes48 compressedPublicKeyBytes) throws BlsException {
    return FakePublicKey.create(DELEGATE.publicKeyFromCompressed(compressedPublicKeyBytes));
  }

  @Override
  public Signature signatureFromCompressed(Bytes compressedSignatureBytes) {
    if (compressedSignatureBytes.equals(INFINITY_SIGNATURE.toBytesCompressed())) {
      return FakeSignature.INF_SIGNATURE;
    } else {
      return FakeSignature.ANY_SIGNATURE;
    }
  }

  @Override
  public SecretKey secretKeyFromBytes(Bytes32 secretKeyBytes) {
    return new FakeSecretKey(DELEGATE.secretKeyFromBytes(secretKeyBytes));
  }

  @Override
  public PublicKey aggregatePublicKeys(List<? extends PublicKey> publicKeys) {
    return FakePublicKey.create(publicKeys.getFirst());
  }

  @Override
  public Signature aggregateSignatures(List<? extends Signature> signatures)
      throws IllegalArgumentException {
    return signatures.isEmpty() ? FakeSignature.INF_SIGNATURE : FakeSignature.ANY_SIGNATURE;
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify(
      int index, List<? extends PublicKey> publicKeys, Bytes message, Signature signature) {
    return FAKE_AGGREGATE;
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify2(
      int index,
      List<? extends PublicKey> publicKeys1,
      Bytes message1,
      Signature signature1,
      List<? extends PublicKey> publicKeys2,
      Bytes message2,
      Signature signature2) {
    return FAKE_AGGREGATE;
  }

  @Override
  public boolean completeBatchVerify(List<? extends BatchSemiAggregate> preparedList) {
    return true;
  }
}
