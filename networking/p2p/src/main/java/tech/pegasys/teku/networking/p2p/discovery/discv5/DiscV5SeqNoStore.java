package tech.pegasys.teku.networking.p2p.discovery.discv5;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import tech.pegasys.teku.storage.store.KeyValueStore;

import java.util.Optional;

public interface DiscV5SeqNoStore {

  static DiscV5SeqNoStore createFromKVStore(KeyValueStore<String, Bytes> kvStore) {
    final String SEQ_NO_STORE_KEY = "local-enr-seqno";

    return new DiscV5SeqNoStore() {
      @Override
      public void putSeqNo(UInt64 seqNo) {
        kvStore.put(SEQ_NO_STORE_KEY, seqNo.toBytes());
      }

      @Override
      public Optional<UInt64> getSeqNo() {
        return kvStore.get(SEQ_NO_STORE_KEY).map(UInt64::fromBytes);
      }
    };
  }

  void putSeqNo(UInt64 seqNo);

  Optional<UInt64> getSeqNo();
}
