package tech.pegasys.teku;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.ActiveEth2P2PNetwork;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.services.beaconchain.BeaconChainController;
import tech.pegasys.teku.services.beaconchain.BeaconChainService;
import tech.pegasys.teku.services.chainstorage.StorageService;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.ChainStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MyTekuTest {

    public static void main(String[] args) throws InterruptedException {
        TekuWithIfc teku = new TekuWithIfc();
        teku.start(args);

        BeaconChainService beaconChainService = teku.getBeaconNode().join().getServiceController().getBeaconChainService();
        BeaconChainController controller = beaconChainService.getController();
        ActiveEth2P2PNetwork p2pNet = (ActiveEth2P2PNetwork) controller.getP2pNetwork();
        DiscoveryNetwork<?> discoveryNetwork = p2pNet.getDiscoveryNetwork();

        StorageService storageService = teku.getBeaconNode().join().getServiceController().getStorageService();
        ChainStorage chainStorage = storageService.getChainStorage();

        Optional<SignedBeaconBlock> earliestBlock = chainStorage.getEarliestAvailableBlock().join();
        System.out.println(earliestBlock);


        UInt64 slot = UInt64.valueOf(2_410_000);
//        Optional<BeaconState> maybeState =
//                controller.getRecentChainData().retrieveStateInEffectAtSlot(slot).join();
        SignedBeaconBlock block = chainStorage.getFinalizedBlockAtSlot(slot).join().orElseThrow();
        Optional<BeaconState> aState = chainStorage.getFinalizedStateByBlockRoot(block.getRoot()).join();
        Optional<Bytes32> blockRootBySlot = controller.getRecentChainData().getBlockRootBySlot(slot);
//        SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(slot, blockRootBySlot.orElseThrow());
//        Optional<BeaconState> maybeState = controller.getRecentChainData().getStore().retrieveStateAtSlot(slotAndBlockRoot).join();
//
//        System.out.println(maybeState);

        System.out.println("Monitoring discovery peers...");
        Map<NodeId, Peer> knownPeers = new HashMap<>();
        while(true) {
            discoveryNetwork.streamPeers().forEach(p -> {
                if (knownPeers.put(p.getId(), p) == null) {
                    System.out.println(knownPeers.size() + ": " + p.getId().toString().substring(20));
                }
            });
            Thread.sleep(1000);
        }
    }

}
