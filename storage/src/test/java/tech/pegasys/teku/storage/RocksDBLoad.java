/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage;

import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.STORAGE_HOT_DB;
import static tech.pegasys.teku.util.config.StateStorageMode.PRUNE;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor.RocksDbTransaction;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbInstanceFactory;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaHot;
import tech.pegasys.teku.util.config.Eth1Address;
import tech.pegasys.teku.util.config.StateStorageMode;

public class RocksDBLoad {

  private static final StateStorageMode DATA_STORAGE_MODE = PRUNE;
  private final Eth1Address eth1Address =
      Eth1Address.fromHexString("0x77f7bED277449F51505a4C54550B074030d989bC");
  @TempDir Path dataDir;
  List<Object> wasteOfHeap = new ArrayList<>();



  @Test
  void load() throws Exception {
    wasteHeap();

    for (int i = 0; i < 2; i++) {
      int finalI = i;
      new Thread(
              () -> {
                try {
                  System.out.println("Starting another DB...");
                  loadDB(dataDir.resolve("sub" + finalI));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              })
          .start();
    }

    Thread.sleep(1000000000L);
  }

  void wasteHeap() {
    System.out.println("Wasting heap");

    int chunkSize = 10 * 1024 * 1024;
    int chunkCount = 0;
    try {
      while (true) {
        wasteOfHeap.add(new byte[chunkSize]);
        chunkCount++;
      }
    } catch (OutOfMemoryError e) {
      System.out.println("Wasted " + chunkCount + " chunks of heap");
      // releasing a bit more
      for (int i = 0; i < 20; i++) {
        wasteOfHeap.set(i, null);
      }
    }
  }

  AtomicInteger cnt = new AtomicInteger();
  volatile long t = System.currentTimeMillis();
  volatile long tGlob;

  void loadDB(Path dataDir) throws Exception {
    RocksDbAccessor dbAccessor =
        RocksDbInstanceFactory.create(
            new StubMetricsSystem(),
            STORAGE_HOT_DB,
            RocksDbConfiguration.v5HotDefaults().withDatabaseDir(dataDir),
            V4SchemaHot.class);

    System.out.println("writing to db :" + dataDir);

    AtomicInteger k = new AtomicInteger();

    for (int j = 0; j < 6; j++) {
      new Thread(
              () -> {
                while (true) {
                  RocksDbTransaction tx = dbAccessor.startTransaction();
                  for (int i = 0; i < 100000; i++) {
                    tx.put(
                        V4SchemaHot.VOTES,
                        UInt64.valueOf(k.getAndIncrement()),
                        VoteTracker.Default());
                  }
                  tx.commit();
                  tx.close();
                  int curCnt = cnt.getAndIncrement();
                  if (curCnt > 0 && curCnt % 16 == 0) {
                    long curT = System.currentTimeMillis();
                    long tt = curT - t;
                    t = curT;
                    if (curCnt == 16) {
                      tGlob = t;
                    } else {
                      System.out.println(
                          "Speed: "
                              + (tt / 16)
                              + " ms/op, total: "
                              + ((t - tGlob) / (curCnt - 16))
                              + " ms/op");
                    }
                  }
                  System.out.println("Committed " + curCnt);
                }
              })
          .start();
    }

    Thread.sleep(1000000000L);
  }
}
