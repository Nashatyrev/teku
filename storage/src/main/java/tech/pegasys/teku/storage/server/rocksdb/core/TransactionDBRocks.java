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

package tech.pegasys.teku.storage.server.rocksdb.core;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.TransactionDB;
import org.rocksdb.WriteOptions;

public class TransactionDBRocks implements TransactionDBIfc {
  private final TransactionDB rdb;

  public TransactionDBRocks(TransactionDB rdb) {
    this.rdb = rdb;
  }

  @Override
  public byte[] get(ColumnFamilyHandle column, byte[] key) throws RocksDBException {
    return rdb.get(column, key);
  }

  @Override
  public void syncWal() throws RocksDBException {
    rdb.syncWal();
  }

  @Override
  public RocksIterator newIterator(ColumnFamilyHandle columnFamilyHandle) {
    return rdb.newIterator(columnFamilyHandle);
  }

  @Override
  public TransactionIfc beginTransaction(WriteOptions writeOptions) {
    return new TransactionRocks(rdb.beginTransaction(writeOptions));
  }
}
