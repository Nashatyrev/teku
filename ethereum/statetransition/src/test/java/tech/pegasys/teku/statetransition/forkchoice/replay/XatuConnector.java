package tech.pegasys.teku.statetransition.forkchoice.replay;

import com.google.common.base.Preconditions;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;

public class XatuConnector implements AutoCloseable {

  public static record Network(String dbNetworkName, Spec spec, UInt64 genesisTime) {}

  public static XatuConnector createDefault(String user, String password) {
    Network mainnet =
        new Network("mainnet", SpecFactory.create("mainnet"), UInt64.valueOf(1606824023000L));
    return new XatuConnector(
        "jdbc:ch://clickhouse.xatu.ethpandaops.io:443?ssl=true&compress=0",
        user,
        password,
        mainnet,
        Set.of(
            "ethpandaops/mainnet/xatu-tysm-ams3-mainnet-003-subnets-0-1",
            "ethpandaops/mainnet/xatu-tysm-ams3-mainnet-005-subnets-0-1"));
  }

  public static XatuConnector createDefault() {
    String clickhouseUser =
        Preconditions.checkNotNull(
            System.getenv("CLICKHOUSE_USER"), "CLICKHOUSE_USER environment variable not found");
    String clickhousePassword =
        Preconditions.checkNotNull(
            System.getenv("CLICKHOUSE_PASSWORD"),
            "CLICKHOUSE_PASSWORD environment variable not found");
    return createDefault(clickhouseUser, clickhousePassword);
  }

  private final int retriesCount = 100000;
  private final int retriesIntervalSec = 30;
  private final long slotTimeShiftMs = 0;

  private final String url;
  private final String user;
  private final String password;
  private final Network network;
  private final Set<String> metaClientNames;

  private Connection connection;
  private PreparedStatement aggregateAttestationStatement;

  public XatuConnector(
      String url, String user, String password, Network network, Set<String> metaClientNames) {
    this.url = url;
    this.user = user;
    this.password = password;
    this.network = network;
    this.metaClientNames = metaClientNames;
  }

  public void connect() {
    String query =
        " SELECT event_date_time, slot, aggregator_index, committee_index, aggregation_bits, "
            + "beacon_block_root, source_epoch, source_root, target_epoch, target_root"
            + " FROM libp2p_gossipsub_aggregate_and_proof "
            + " WHERE"
            + "   slot_start_date_time BETWEEN ? AND ? "
            + "   AND event_date_time BETWEEN ? AND ? "
            + "   AND meta_network_name = ? "
            + "   AND meta_client_name IN ?";
    try {
      connection = DriverManager.getConnection(url, user, password);
      aggregateAttestationStatement = connection.prepareStatement(query);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public List<Attestation> getAttestationsReceivedDuringSlot(UInt64 slot) {
    return getAttestationsReceivedDuringSlots(slot, slot.increment()).getFirst();
  }

  private static record InstantPeriod(Instant startInstant, Instant endInstant) {
    public InstantPeriod shift(long ms) {
      return new InstantPeriod(startInstant.plusMillis(ms), endInstant.plusMillis(ms));
    }
  }

  private InstantPeriod getSlotPeriod(UInt64 slot) {
    return getSlotsPeriod(slot, slot.increment());
  }

  private UInt64 getSlot(Instant timestamp) {
    return network.spec.getCurrentSlotFromTimeMillis(
        UInt64.valueOf(timestamp.toEpochMilli()), network.genesisTime());
  }

  private InstantPeriod getSlotsPeriod(UInt64 startSlot, UInt64 endSlot) {
    UInt64 slotStart = network.spec().computeTimeMillisAtSlot(startSlot, network.genesisTime());
    UInt64 slotEnd = network.spec().computeTimeMillisAtSlot(endSlot, network.genesisTime());
    return new InstantPeriod(
        Instant.ofEpochMilli(slotStart.longValue()), Instant.ofEpochMilli(slotEnd.longValue()));
  }

  public record SlotAttestations(UInt64 slot, List<Attestation> attestations) {}

  public BlockingQueue<SlotAttestations> streamAttestationsReceivedDuringNextSlots(
      UInt64 startSlot) {
    BlockingQueue<SlotAttestations> queue = new LinkedBlockingDeque<>(100);
    int batchSize = 10;
    new Thread(
            () -> {
              UInt64 batchFirstSlot = startSlot;
              while (true) {
                long s = System.currentTimeMillis();
                UInt64 batchEndSlot = batchFirstSlot.plus(batchSize);
                try {
                  List<List<Attestation>> attestationsReceivedDuringSlots =
                      getAttestationsReceivedDuringSlots(batchFirstSlot, batchEndSlot);
                  for (int i = 0; i < batchSize; i++) {
                    queue.put(
                        new SlotAttestations(
                            batchFirstSlot.plus(i), attestationsReceivedDuringSlots.get(i)));
                  }
                  System.out.println(
                      "Preloaded attestation batch ("
                          + batchFirstSlot
                          + " - "
                          + batchEndSlot
                          + ") in "
                          + (System.currentTimeMillis() - s)
                          + " ms, queue size: "
                          + queue.size());
                  batchFirstSlot = batchEndSlot;
                } catch (Exception e) {
                  e.printStackTrace();
                  try {
                    Thread.sleep(60000);
                  } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                  }
                }
              }
            },
            "XatuConnector-Prefetch-Thread")
        .start();

    return queue;
  }

  public List<List<Attestation>> getAttestationsReceivedDuringSlots(
      UInt64 startSlot, UInt64 endSlot) {
    long s = System.currentTimeMillis();
    InstantPeriod instantPeriod = getSlotsPeriod(startSlot, endSlot);
    InstantPeriod shiftedPeriod = instantPeriod.shift(slotTimeShiftMs);
    ResultSet resultSet =
        getAggregatesReceivedInPeriod(shiftedPeriod.startInstant, shiftedPeriod.endInstant);

    try {
      List<List<Attestation>> ret =
          Stream.generate(() -> (List<Attestation>) new ArrayList<Attestation>())
              .limit(endSlot.intValue() - startSlot.intValue())
              .toList();

      while (resultSet.next()) {
        Checkpoint source =
            new Checkpoint(
                UInt64.valueOf(resultSet.getInt(7)), Bytes32.fromHexString(resultSet.getString(8)));
        Checkpoint target =
            new Checkpoint(
                UInt64.valueOf(resultSet.getInt(9)),
                Bytes32.fromHexString(resultSet.getString(10)));
        AttestationData attestationData =
            new AttestationData(
                UInt64.valueOf(resultSet.getInt(2)),
                UInt64.ZERO,
                Bytes32.fromHexString(resultSet.getString(6)),
                source,
                target);

        SchemaDefinitions schemaDefinitions =
            network.spec().atSlot(startSlot).getSchemaDefinitions();
        AttestationSchema<Attestation> attestationSchema = schemaDefinitions.getAttestationSchema();
        SszBitlist aggregationBits =
            attestationSchema.getAggregationBitsSchema().fromHexString(resultSet.getString(5));
        SszBitvectorSchema<?> committeeBitsSchema =
            attestationSchema
                .getCommitteeBitsSchema()
                .orElseThrow(() -> new RuntimeException("Only Electra fork supported for now"));
        SszBitvector committeeBits = committeeBitsSchema.ofBits(resultSet.getInt(4));
        Attestation attestation =
            attestationSchema.create(
                aggregationBits, attestationData, BLSSignature.infinity(), () -> committeeBits);

        Instant eventTime = resultSet.getTimestamp(1, UTC).toInstant();
        if (eventTime.equals(shiftedPeriod.endInstant())) {
          // looks like BETWEEN SQL treat end or range inclusively
          continue;
        }

        Instant shiftedEventTime = eventTime.plusMillis(-slotTimeShiftMs);
        ret.get(getSlot(shiftedEventTime).minus(startSlot).intValue()).add(attestation);
      }
      System.out.println(
          "Loaded " + ret.size() + " attestations in " + (System.currentTimeMillis() - s) + " ms");
      return ret;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

  private ResultSet getAggregatesReceivedInPeriod(Instant from, Instant to) {
    int retries = 0;
    while (true) {
      try {
        Instant roughFrom = from.minus(1, ChronoUnit.HOURS);
        Instant roughTo = to.plus(10, ChronoUnit.MINUTES);

        aggregateAttestationStatement.setTimestamp(1, Timestamp.from(roughFrom));
        aggregateAttestationStatement.setTimestamp(2, Timestamp.from(roughTo));
        aggregateAttestationStatement.setTimestamp(3, Timestamp.from(from));
        aggregateAttestationStatement.setTimestamp(4, Timestamp.from(to));
        aggregateAttestationStatement.setString(5, network.dbNetworkName());
        aggregateAttestationStatement.setObject(6, metaClientNames.toArray(new String[0]));

        return aggregateAttestationStatement.executeQuery();
      } catch (SQLException e) {
        if (retries == retriesCount) {
          throw new RuntimeException(
              "Couldn't retrieve data after " + retriesCount + " retries", e);
        }
        System.out.println("Trial #" + retries + " failed with " + e);
        retries++;
        try {
          Thread.sleep(Duration.ofSeconds(retriesIntervalSec));
        } catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }
}
