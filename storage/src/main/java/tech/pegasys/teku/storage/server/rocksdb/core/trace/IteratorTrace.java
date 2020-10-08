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

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.tuweni.bytes.Bytes;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

public class IteratorTrace extends RocksIterator {
  private static final AtomicLong counter = new AtomicLong();

  private final RocksIterator delegate;
  private final long itId;

  public IteratorTrace(RocksIterator delegate) {
    super(null, 0);
    this.delegate = delegate;
    this.itId = counter.incrementAndGet();
  }

  public long getItId() {
    return itId;
  }

  @Override
  public byte[] key() {
    long s = System.nanoTime();
    byte[] ret = delegate.key();
    long t = System.nanoTime() - s;
    Utils.write("it_key " + getItId() + " " + t);
    return ret;
  }

  @Override
  public byte[] value() {
    long s = System.nanoTime();
    byte[] ret = delegate.value();
    long t = System.nanoTime() - s;
    Utils.write("it_value " + getItId() + " " + ret.length + " " + t);
    return ret;
  }

  @Override
  public boolean isValid() {
    long s = System.nanoTime();
    boolean ret = delegate.isValid();
    long t = System.nanoTime() - s;
    Utils.write("it_is_valid " + getItId() + " " + t);
    return ret;
  }

  @Override
  public void seekToFirst() {
    long s = System.nanoTime();
    delegate.seekToFirst();
    long t = System.nanoTime() - s;
    Utils.write("it_seek_first " + getItId() + " " + t);
  }

  @Override
  public void seekToLast() {
    long s = System.nanoTime();
    delegate.seekToLast();
    long t = System.nanoTime() - s;
    Utils.write("it_seek_last " + getItId() + " " + t);
  }

  @Override
  public void seek(byte[] target) {
    long s = System.nanoTime();
    delegate.seek(target);
    long t = System.nanoTime() - s;
    Utils.write("it_seek " + getItId() + " " + Bytes.wrap(target) + " " + t);
  }

  @Override
  public void seekForPrev(byte[] target) {
    long s = System.nanoTime();
    delegate.seekForPrev(target);
    long t = System.nanoTime() - s;
    Utils.write("it_seek_prev " + getItId() + " " + Bytes.wrap(target) + " " + t);
  }

  @Override
  public void next() {
    long s = System.nanoTime();
    delegate.next();
    long t = System.nanoTime() - s;
    Utils.write("it_next " + getItId() + " " + t);
  }

  @Override
  public void status() throws RocksDBException {
    delegate.status();
  }

  @Override
  public void close() {
    long s = System.nanoTime();
    delegate.close();
    long t = System.nanoTime() - s;
    Utils.write("it_close " + getItId() + " " + t);
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
