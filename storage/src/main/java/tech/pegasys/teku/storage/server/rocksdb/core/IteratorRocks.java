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

import java.nio.ByteBuffer;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

public class IteratorRocks extends RocksIterator {
  private final RocksIterator delegate;

  public IteratorRocks(RocksIterator delegate) {
    super(null, 0);
    this.delegate = delegate;
  }

  @Override
  public byte[] key() {
    return delegate.key();
  }

  @Override
  public byte[] value() {
    return delegate.value();
  }

  @Override
  public boolean isValid() {
    return delegate.isValid();
  }

  @Override
  public void seekToFirst() {
    delegate.seekToFirst();
  }

  @Override
  public void seekToLast() {
    delegate.seekToLast();
  }

  @Override
  public void seek(byte[] target) {
    delegate.seek(target);
  }

  @Override
  public void seekForPrev(byte[] target) {
    delegate.seekForPrev(target);
  }

  @Override
  public void next() {
    delegate.next();
  }

  @Override
  public void status() throws RocksDBException {
    delegate.status();
  }

  @Override
  public void close() {
    delegate.close();
  }

  /** * NOT USED ** */
  @Override
  public int key(ByteBuffer key) {
    return delegate.key(key);
  }

  @Override
  public int value(ByteBuffer value) {
    return delegate.value(value);
  }

  @Override
  public void seek(ByteBuffer target) {
    delegate.seek(target);
  }

  @Override
  public void seekForPrev(ByteBuffer target) {
    delegate.seekForPrev(target);
  }

  @Override
  public void prev() {
    delegate.prev();
  }

  @Override
  public void refresh() throws RocksDBException {
    delegate.refresh();
  }

  @Override
  public boolean isOwningHandle() {
    return delegate.isOwningHandle();
  }
}
