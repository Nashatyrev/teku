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

package tech.pegasys.teku.storage.server.rocksdb.core.trace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

public class Utils {
  private static final Logger LOG = LogManager.getLogger();

  static final File file = new File("rocksdb.trace");
  static final BufferedWriter writer;
  static final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>();

  static {
    try {
      writer = new BufferedWriter(new FileWriter(file, StandardCharsets.US_ASCII));
      new Thread(
              () -> {
                try {
                  while (true) {
                    writer.write(writeQueue.take());
                  }
                } catch (IOException | InterruptedException e) {
                  LOG.error("Err", e);
                }
              })
          .start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String getColumnName(ColumnFamilyHandle column) {
    try {
      return Bytes.wrap(column.getName()).toShortHexString();
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  static void write(String s) {
    try {
      writeQueue.put("[" + System.currentTimeMillis() + "] " + s + "\n");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
