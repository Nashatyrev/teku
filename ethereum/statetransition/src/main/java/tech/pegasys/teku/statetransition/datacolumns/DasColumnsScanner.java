package tech.pegasys.teku.statetransition.datacolumns;

import static tech.pegasys.teku.infrastructure.logging.LogFormatter.formatAbbreviatedHashRoot;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.statetransition.datacolumns.retriever.BatchDataColumnsByRangeReqResp;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnPeerManager;
import tech.pegasys.teku.statetransition.datacolumns.util.StringifyUtil;

public class DasColumnsScanner {
  private static final Logger LOG = LogManager.getLogger("das-scanner");

  private static final int maxScanAttempt = 20;
  private static final int columnCount = 128;
  private static final List<UInt64> allColIndexes =
      IntStream.range(0, columnCount).mapToObj(UInt64::valueOf).toList();

  private final BatchDataColumnsByRangeReqResp byRangeRpc;
  private final List<UInt64> scanSlots;
  private final CustodyCalculator custodyCalculator;

  private final Map<UInt256, String> nodeNames;
  private final Map<UInt256, PeerScanner> scannedPeers = new HashMap<>();

  public DasColumnsScanner(
      DataColumnPeerManager peerManager,
      BatchDataColumnsByRangeReqResp byRangeRpc,
      List<UInt64> scanSlots,
      CustodyCalculator custodyCalculator) {
    this.byRangeRpc = byRangeRpc;
    this.scanSlots = scanSlots;
    this.custodyCalculator = custodyCalculator;
    peerManager.addPeerListener(
        new DataColumnPeerManager.PeerListener() {
          @Override
          public void peerConnected(UInt256 nodeId) {
            onPeerConnected(nodeId);
          }

          @Override
          public void peerDisconnected(UInt256 nodeId) {}
        });
    nodeNames =
        NODE_NAMES
            .lines()
            .map(
                l -> {
                  String[] ss = l.split(" ");
                  UInt256 nodeId = UInt256.fromHexString(ss[0]);
                  String nodeName = ss[1];
                  return Map.entry(nodeId, nodeName);
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private synchronized void onPeerConnected(UInt256 nodeId) {
    if (!scannedPeers.containsKey(nodeId)) {
      String nodeName = nodeNames.getOrDefault(nodeId, nodeId.toHexString().substring(0, 10));
      PeerScanner peerScanner = new PeerScanner(nodeId, nodeName);
      scannedPeers.put(nodeId, peerScanner);
      peerScanner.nextScan();
    }
  }

  private synchronized void onPeerScanComplete(PeerScanner peerScanner) {
    if (scannedPeers.values().stream().allMatch(PeerScanner::isComplete)) {
      System.err.println("################## DasScan #########################");
      scannedPeers.values().stream()
          .sorted(Comparator.comparing(p -> p.nodeName))
          .forEach(
              scan -> {
                System.err.print("  ");
                scan.print();
              });
    }
  }

  class PeerScanner {
    private final UInt256 nodeId;
    private final String nodeName;

    private final SortedMap<UInt64, ScanSlotResult> slotResults = new TreeMap<>();
    private boolean complete = false;

    public PeerScanner(UInt256 nodeId, String nodeName) {
      this.nodeId = nodeId;
      this.nodeName = nodeName;
      scanSlots.forEach(slot -> slotResults.put(slot, new ScanSlotResult(slot)));
    }

    private void nextScan() {
      Optional<ScanSlotResult> maybeNotYetScanned =
          slotResults.values().stream().filter(ScanSlotResult::isNeverScanned).findFirst();
      Optional<ScanSlotResult> slotToScan =
          maybeNotYetScanned.or(
              () ->
                  slotResults.values().stream()
                      .filter(r -> !r.isDone())
                      .min(Comparator.comparing(res -> res.attemptCount)));
      slotToScan.ifPresentOrElse(this::scan, this::onComplete);
    }

    private void onComplete() {
      System.out.print("### DasScan: ");
      print();
      this.complete = true;
      onPeerScanComplete(this);
    }

    public void print() {
      System.out.println(nodeName);
      slotResults.forEach((slot, res) -> System.out.println("      " + res));
    }

    public boolean isComplete() {
      return complete;
    }

    private void scan(ScanSlotResult slotRes) {
      slotRes.onNextAttemptStarted();
      LOG.debug("Requesting columns from slot " + slotRes.slot + " from " + nodeName);
      Collection<UInt64> peerCustodyColumnIndexes =
          custodyCalculator.computeCustodyColumnIndexes(
              nodeId, slotRes.slot);
      AsyncStream<DataColumnSidecar> sidecarsStream =
          byRangeRpc.requestDataColumnSidecarsByRange(
              nodeId, slotRes.slot, 1, List.copyOf(peerCustodyColumnIndexes));
      sidecarsStream
          .toList()
          .finish(resp -> onResponse(slotRes, resp), err -> onError(slotRes, err));
    }

    private void onResponse(ScanSlotResult slotRes, List<DataColumnSidecar> resp) {
      Map<Bytes32, List<DataColumnSidecar>> sidecarsByBlock =
          resp.stream().collect(Collectors.groupingBy(DataColumnSidecar::getBlockRoot));
      slotRes.setScanBlocks(
          sidecarsByBlock.values().stream()
              .map(
                  sidecars -> {
                    DataColumnSidecar anySidecar = sidecars.getFirst();
                    List<Integer> columnIndexes =
                        sidecars.stream().map(s -> s.getIndex().intValue()).toList();
                    return new ScanBlockResult(
                        anySidecar.getBlockRoot(),
                        anySidecar.getSignedBeaconBlockHeader().getMessage().getParentRoot(),
                        columnIndexes);
                  })
              .toList());
      nextScan();
    }

    private void onError(ScanSlotResult slotRes, Throwable err) {
      slotRes.setError(err);
      nextScan();
    }
  }

  class ScanSlotResult {
    private final UInt64 slot;
    List<ScanBlockResult> scanBlocks = null;
    Throwable error = null;
    int attemptCount = 0;

    public ScanSlotResult(UInt64 slot) {
      this.slot = slot;
    }

    public synchronized void onNextAttemptStarted() {
      attemptCount++;
    }

    public synchronized boolean isNeverScanned() {
      return attemptCount == 0;
    }

    public synchronized boolean isDone() {
      return scanBlocks != null || attemptCount >= maxScanAttempt;
    }

    public synchronized void setError(Throwable error) {
      this.error = error;
    }

    public synchronized void setScanBlocks(List<ScanBlockResult> scanBlocks) {
      this.scanBlocks = scanBlocks;
    }

    @Override
    public synchronized String toString() {
      String ret = "Slot " + slot + ": ";
      if (isNeverScanned()) {
        ret += "---";
      } else if (scanBlocks != null) {
        if (scanBlocks.isEmpty()) {
          ret += "<empty>";
        } else {
          ret +=
              scanBlocks.stream().map(ScanBlockResult::toString).collect(Collectors.joining(","));
        }
        if (attemptCount > 1) {
          ret += " (" + attemptCount + " attempts)";
        }
      } else if (error != null) {
        ret += "error (" + attemptCount + " attempts), last err: " + error;
      }
      return ret;
    }
  }

  record ScanBlockResult(Bytes32 blockRoot, Bytes32 parentBlockRoot, List<Integer> columns) {
    @Override
    public String toString() {
      return formatAbbreviatedHashRoot(blockRoot)
          + " <~ "
          + formatAbbreviatedHashRoot(parentBlockRoot)
          + ", columns: "
          + StringifyUtil.columnIndexesToString(columns, columnCount);
    }
  }

  private static final String NODE_NAMES =
      """
0x3e184a5206a0d449ec7029322bf86a0bc27d5872e040dde490119a4ba9adaf6b bootnode-1
0xd1abfb2b638b965a636b3c722e32f26971ab93802ea2ccf26bf6c3ea1cbb531c grandine-geth-1
0xe2e00102f9f387cd35da777fc7aed43fd3f87503edf77f17e4addcd0bf25644b grandine-geth-1-arm
0x4e293bd1f609e389e788f07fcdf3e05449040ced7131f2fc7862ad57e39ef36d grandine-geth-2
0x0055c173abe2167b981dd5876449091a845e813aa6809eb5a2460633c2925669 grandine-geth-2-arm
0xc36faa947cd537ed91c3f5cb9b0c24ab36deb37ee0311c2f3dc5ccdf76084e78 grandine-geth-3
0x0f2ebeca4b75d58dfbc8ba0255af800a44d6746496648318af53c43af3a0ddcd grandine-geth-3-arm
0x400f55e084349a2c03334f6d250d8ad8a25041b58507be5109bd96dd1bef295a grandine-geth-4
0x20d4bd3eebcd929387717581a9c35c1f5fac492bbfc53905b8c03e01c01aa6d8 grandine-geth-5
0xdc0d97f31e352c215ce388eb1f288555b124ab7f6fb90432939245afbf9e73ab grandine-geth-6
0x591ef23e7c0fed7a84494771ceb36bcd2dff99c892f72d9d45c48841b5f29edb lighthouse-geth-1
0xfecac6a0062fa174adcdd98c030ccb12fe05787caaad64dcb77e2def9f93861b lighthouse-geth-1-arm
0xd35881ba57a253d7716a1682440049255d1224d0443248a066e81a405913ab80 lighthouse-geth-2
0x0e7c8bacadddf7d059602e7f6952ef7f1564e40f34bb598c55f6cc8913ff41db lighthouse-geth-2-arm
0xc52b998db90fe9b71e5c21193a1e2d7b3c5bab34e31787210c85ad433372730b lighthouse-geth-3
0x17f9cc3fddc457b459f0c274ddc4130912e8e82840c3c869ab707cf6f886f0df lighthouse-geth-3-arm
0xe37d8b3c2681283d6eb4f8ea353a34722fc8c6bfaa5334419364d447369ba6f2 lighthouse-geth-4
0x3e373cbc3158c78f223d47ad3e3eb3e188f527b2545bdc12be9a944b2ea77fa6 lighthouse-geth-5
0xc0075f203e4816ff31efe4766be2280ce8e07d7e8e81e9d9eaab3a58d2265a0b lighthouse-geth-6
0x14e84f09f47c53810318b575ebc18fd0915456e330cfdff76c6d4a482dff39da lodestar-geth-1
0x5dd3c5333609d6e64aa2385e91972e5d9f7a1787f2e27e7c0efbed3c685def15 lodestar-geth-1-arm
0x6f73827957646bf28c97c7b4e10e2e4e7cae1a1889e86dfadf7c955872917512 lodestar-geth-2
0xea39c8df88bd9adb51b5d8be467d97875963601a22e330c2a86e4a837500ff22 lodestar-geth-2-arm
0xdacb5df172d42a830be8dfead748ba572ba45c6407e3fdf8766c10ab44331f85 lodestar-geth-3
0xc7c69681bc1ca4a271a509981b0948a6b58e6a2e809ae6b768946aeab4877434 lodestar-geth-3-arm
0xe3142f83895c78abc952ca9845502828c7fa47401792e6bdf9eee7e70379ff58 lodestar-geth-4
0x7cd7ec947a9f120e3aa2db7d29438f77eaa097c2339a7733b9b6224e4905d4ad lodestar-geth-5
0xaf5ba3fcdbd5528b9da131c0bf23d025842d759392006e007a1e6262b7458cca lodestar-geth-6
0xd9e9c7875577a5f51cf401cadb6a173b6347b02797f6f61d45e46a7cdf6067ee nimbus-geth-1
0x6e991dc591a73c730a34242d670b2a860466e94e2fa5ed55fcb2c8862b0fb409 nimbus-geth-1-arm
0x3a5fbd80cab2554010f732aa854bb2077d3c4403f77ff6ad0a2623b9d7c84fa2 nimbus-geth-2
0xaa3f3d31dc70ad542887d06d389b981272b8c207d337306882f67784848cae5a nimbus-geth-2-arm
0xe22b448cafe854c94edf40948eb4bc99715e9bbafbe9266c1ffe1333852b914b nimbus-geth-3
0x4b661a02438f6ca7e62b0a1d80c5ff8faeb68e836ab634ba9852f3c402834ac5 nimbus-geth-3-arm
0x741b0c3134660296532a9b268c7a90501fe42f48f46fdc3b503ed031cd730fe7 nimbus-geth-4
0x63a0d2760d37204f5e919943ae53b5d470473d0d246721882653ed40518ccc83 nimbus-geth-5
0x945926989fea517aea898777a5a085494420f31bcb9ed8d04f726aaddfe7e678 nimbus-geth-6
0xa77acb9a6fa4ddddacedf1f3f30975a9f0b365697fe5cbc8492ebe8352ef76d7 prysm-geth-1
0xe0dbad5c5506c5758f6aff0f5466ccce434b17900f61cbe218adc550810ebc47 prysm-geth-1-arm
0x97553ffcac8c1853d4cc72428ecbfbb79c8be318ac35605e41bf2d276d51e5e7 prysm-geth-2
0xd1bcb77fcdb1377c5ab037ba3f353f9b23a76b9683453948f5841cf69ea4aa72 prysm-geth-2-arm
0x7da4e4abffa14abf08fd5e9e22b39732db9f655a5713195440ed963fbea95526 prysm-geth-3
0xfa2bb7c71dc935627d4a113ec424cfd1b19e3ba1f4ae3978a72dd70550500cb9 prysm-geth-3-arm
0x05ca2aabd72e42e2ea3c9c38fd387da95330781342863bd8b637db16da90679f prysm-geth-4
0x285c2c8b30edb0350d303646463169d323a9b9381f6acac998faf69e0dac4a15 prysm-geth-5
0xc66a65f958a8e6a165db76705608256ca450d2767ca904a68ff3cce9176f0f9b prysm-geth-6
0x48a3d027c8e05bff1baf9b60be32708791a5bc9fa99f56b4099b5985402e6e35 teku-geth-1
0x1c8da93eb7d56ce68e4230f81d89da76ab5cf098e20183f52681db4efda11584 teku-geth-1-arm
0xc8017464b7782d04de14f9d267f0caddcd9fa7a41f59f5ea6bfc360ee5be5c2d teku-geth-2
0xbfe36d5bc3ccb18b22161c4e25d0ba2c8e08873b6d93ff4d3e18a0a5b90e73c9 teku-geth-2-arm
0x03fa68ed4980c0cec89aea52270b51aa5770e1b50ba86375e7201b81f6bdd03a teku-geth-3
0x1362e320d1ccc6311b3fab428218fcd7286465b7ee9f3e2d54d2d658c5c5c632 teku-geth-3-arm
0x869218e71798f50a64d4d50e87aa071599b703224be05f045965d8b196a89e97 teku-geth-4
0x988ba3401c534d9bab9dcee2f1c37c292c21f7e04201d1fbfcc6f3c41cdc2e32 teku-geth-5
0x7494d4e8354bf68d967d0cd65ab56d75fc30ae9a267130c58a16bce12f60cdfb teku-geth-6
""";
}
