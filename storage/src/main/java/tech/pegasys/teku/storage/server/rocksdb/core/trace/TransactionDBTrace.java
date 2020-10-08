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
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteOptions;
import tech.pegasys.teku.storage.server.rocksdb.core.TransactionDBIfc;
import tech.pegasys.teku.storage.server.rocksdb.core.TransactionIfc;

public class TransactionDBTrace implements TransactionDBIfc {
  private static final AtomicLong counter = new AtomicLong();
  private final TransactionDBIfc delegate;
  private final long dbId = counter.incrementAndGet();

  public TransactionDBTrace(TransactionDBIfc delegate) {
    this.delegate = delegate;
  }

  @Override
  public byte[] get(ColumnFamilyHandle column, byte[] key) throws RocksDBException {
    long s = System.nanoTime();
    byte[] ret = delegate.get(column, key);
    long t = System.nanoTime() - s;
    Utils.write(
        "get "
            + dbId
            + " "
            + Utils.getColumnName(column)
            + " "
            + Bytes.wrap(key)
            + " "
            + (ret != null ? ret.length : "0")
            + " "
            + t);
    return ret;
  }

  @Override
  public void syncWal() throws RocksDBException {
    delegate.syncWal();
  }

  @Override
  public RocksIterator newIterator(ColumnFamilyHandle column) {
    IteratorTrace ret = new IteratorTrace(delegate.newIterator(column));
    Utils.write("iterator " + dbId + " " + Utils.getColumnName(column) + " " + ret.getItId());
    return delegate.newIterator(column);
  }

  @Override
  public TransactionIfc beginTransaction(WriteOptions writeOptions) {

    TransactionTrace txt = new TransactionTrace(delegate.beginTransaction(writeOptions));

    Utils.write("transaction " + dbId + " " + txt.getTxId());
    return txt;
  }
}
