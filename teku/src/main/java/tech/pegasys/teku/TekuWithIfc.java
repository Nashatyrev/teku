/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.cli.BeaconNodeCommand;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.ifc.BeaconNodeIfc;
import tech.pegasys.teku.ifc.TekuIfc;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.security.Security;

public final class TekuWithIfc implements TekuIfc {
  private BeaconNode beaconNode;

  public void start(final String... args) {
    Thread.setDefaultUncaughtExceptionHandler(new TekuDefaultExceptionHandler());
    Security.addProvider(new BouncyCastleProvider());
    final PrintWriter outputWriter = new PrintWriter(System.out, true, Charset.defaultCharset());
    final PrintWriter errorWriter = new PrintWriter(System.err, true, Charset.defaultCharset());
    final int result =
        new BeaconNodeCommand(outputWriter, errorWriter, System.getenv(), this::start).parse(args);
    if (result != 0) {
      System.exit(result);
    }
  }

  private void start(final TekuConfiguration config, final boolean validatorOnly) {
    final Node node;
    if (validatorOnly) {
      node = new ValidatorNode(config);
    } else {
      beaconNode = new BeaconNode(config);
      node = beaconNode;
    }
    // Check that BLS is available before starting to ensure we get a nice error message if it's not
    if (BlstLoader.INSTANCE.isEmpty()) {
      throw new UnsupportedOperationException("BLS native library unavailable for this platform");
    }

    node.start();
    // Detect SIGTERM
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("Teku is shutting down");
                  node.stop();
                }));
  }

  @Override
  public SafeFuture<BeaconNodeIfc> getBeaconNode() {
    return SafeFuture.completedFuture(beaconNode);
  }
}
