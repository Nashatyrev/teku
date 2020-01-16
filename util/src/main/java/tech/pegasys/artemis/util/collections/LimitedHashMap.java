package tech.pegasys.artemis.util.collections;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/**
 * Map with limited capacity. When map size overflows max capacity the eldest entry is dropped
 */
public class LimitedHashMap<K, V> extends LinkedHashMap<K, V> {
  private final int maxSize;

  public LimitedHashMap(int maxSize) {
    this.maxSize = maxSize;
  }

  @Override
  protected boolean removeEldestEntry(Entry<K, V> eldest) {
    return size() > maxSize;
  }
}
