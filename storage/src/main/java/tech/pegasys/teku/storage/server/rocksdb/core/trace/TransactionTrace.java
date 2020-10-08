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

import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import tech.pegasys.teku.storage.server.rocksdb.core.TransactionIfc;

public class TransactionTrace implements TransactionIfc {
  private static final AtomicLong counter = new AtomicLong();

  private final TransactionIfc delegate;
  private final long txId;

  public TransactionTrace(TransactionIfc delegate) {
    this.delegate = delegate;
    this.txId = counter.incrementAndGet();
  }

  public long getTxId() {
    return txId;
  }

  @Override
  public void put(ColumnFamilyHandle column, byte[] key, byte[] value) throws RocksDBException {
    long s = System.nanoTime();
    delegate.put(column, key, value);
    long t = System.nanoTime() - s;
    Utils.write(
        "tx_put "
            + getTxId()
            + " "
            + Utils.getColumnName(column)
            + " "
            + Bytes.wrap(key)
            + " "
            + value.length
            + " "
            + t);
  }

  @Override
  public void delete(ColumnFamilyHandle column, byte[] key) throws RocksDBException {
    long s = System.nanoTime();
    delegate.delete(column, key);
    long t = System.nanoTime() - s;
    Utils.write(
        "tx_delete "
            + getTxId()
            + " "
            + Utils.getColumnName(column)
            + " "
            + Bytes.wrap(key)
            + " "
            + t);
  }

  @Override
  public void commit() throws RocksDBException {
    long s = System.nanoTime();
    delegate.commit();
    long t = System.nanoTime() - s;
    Utils.write("tx_commit " + getTxId() + " " + t);
  }

  @Override
  public void close() {
    long s = System.nanoTime();
    delegate.close();
    long t = System.nanoTime() - s;
    Utils.write("tx_close " + getTxId() + " " + t);
  }
}
