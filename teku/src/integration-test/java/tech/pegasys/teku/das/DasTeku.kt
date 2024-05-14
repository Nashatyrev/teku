package tech.pegasys.teku.das

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import io.libp2p.core.crypto.unmarshalPrivateKey
import org.apache.logging.log4j.Level
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.units.bigints.UInt256
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.bls.BLSKeyPair
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.EncryptedKeystoreWriter
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.logging.LoggingConfig
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator
import tech.pegasys.teku.infrastructure.logging.LoggingDestination
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.TestSpecFactory
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.spec.util.DataStructureUtil
import tech.pegasys.teku.storage.server.StateStorageMode
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*


class RunBootNode {
    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val dasTeku = DasTeku()
            dasTeku.createGenesisIfRequired()
            dasTeku.resetWithNewGenesis()
            dasTeku.createAndStartBootNode(0, 0 until 32)
        }
    }
}

class RunOtherNode {
    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val dasTeku = DasTeku()
            dasTeku.createAndStartNode(1, 32 until 64)
        }
    }
}

const val STARTUP_TIME_SECONDS = 7

class DasTeku(
    val validatorsCount: Int = 64,
    val spec: Spec = TestSpecFactory.createMinimalEip7594 {
        it.bellatrixBuilder { it.bellatrixForkEpoch(UInt64.valueOf(0)) }
        it.capellaBuilder { it.capellaForkEpoch(UInt64.valueOf(0)) }
        it.denebBuilder { it.denebForkEpoch(UInt64.valueOf(0)) }
        it.eip7594Builder {
            it.eip7594ForkEpoch(UInt64.valueOf(0))
            it.custodyRequirement(2)
        }
        it.secondsPerSlot(8)
        it.slotsPerEpoch(8)
        it.eth1FollowDistance(UInt64.valueOf(1))
    },
    val extraDasCustodySubnetCount: Int = 32,
    val validatorDepositAmount: UInt64 = spec.genesisSpecConfig.maxEffectiveBalance * 100,
    val stateStorageMode: StateStorageMode = StateStorageMode.PRUNE,

    val workDir: String = "./work.dir/das",
    val advertisedIp: String = "127.0.0.1",
) {

    val genesisFile = "$workDir/genesis.ssz"
    val validatorKeyPass = "1234"
    val bootnodeEnrFile = "$workDir/bootnode-enr.txt"

    val random = createDeterministicRandom()
    val validatorKeys: List<BLSKeyPair> = List(validatorsCount) { BLSKeyPair.random(random) }

    fun createGenesisIfRequired() {
        if (!File(genesisFile).exists()) {
            resetWithNewGenesis()
        }
    }

    fun resetWithNewGenesis() {
        File(workDir).also {
            if (!it.deleteRecursively()) {
                throw RuntimeException("Couldn't delete $workDir")
            }
            it.mkdirs()
        }
        writeGenesis()
    }

    fun createAndStartBootNode(
        number: Int,
        validators: IntRange
    ) {
        val bootNodeConfig = createNodeConfig(number, validatorKeys.slice(validators), null)
        val enr = createEnrFromTekuConfig(bootNodeConfig)
        File(bootnodeEnrFile).writeText(enr)
        TekuFacade.startBeaconNode(bootNodeConfig)
    }

    fun createAndStartNode(
        number: Int,
        validators: IntRange
    ) {
        val bootnodeEnr = File(bootnodeEnrFile).readText()
        val nodeConfig = createNodeConfig(number, validatorKeys.slice(validators), bootnodeEnr)
        TekuFacade.startBeaconNode(nodeConfig)
    }

    fun createNodeConfig(
        number: Int,
        validators: List<BLSKeyPair>,
        bootnodeEnr: String?
    ): TekuConfiguration {
        val port = 9004 + number
        val dataPath = "$workDir/node-$number"
        val validatorKeysPath = "$dataPath/keys"
        val nodePrivKey = generatePrivateKeyFromSeed(port.toLong())

        writeValidatorKeys(validators, validatorKeysPath)

        val tekuConfigBuilder =
            TekuConfiguration.builder()
                .eth2NetworkConfig {
                    it
                        .applyNetworkDefaults(Eth2Network.MINIMAL)
                        .customGenesisState(genesisFile)
                        .totalTerminalDifficultyOverride(UInt256.ZERO)
                        .spec(spec)
                    if (bootnodeEnr != null) {
                        it.discoveryBootnodes(bootnodeEnr)
                    }
                }
                .discovery {
                    it
                        .maxPeers(32)
                        .minPeers(6)
                        .isDiscoveryEnabled(true)
                        .siteLocalAddressesEnabled(true)
                }
                .p2p {
                    it
                        // TODO with default value one Teku disconnects the other Teku with
                        // 'disconnected due to request rate limits' on blobSidecar RPC
                        .peerRateLimit(1000000)
                        .peerRequestLimit(1000000)
                        .subscribeAllSubnetsEnabled(true)
                        .dasExtraCustodySubnetCount(extraDasCustodySubnetCount)
                }
                .network {
                    it
                        .listenPort(port)
                        .advertisedIp(Optional.of(advertisedIp))
                        .setPrivateKeySource { Bytes.wrap(nodePrivKey.bytes()) }
                }
                .data {
                    it
                        .dataBasePath(Path.of(dataPath))
                }
                .storageConfiguration {
                    it
                        .dataStorageMode(stateStorageMode)
                }
                .executionLayer {
                    it
                        .engineEndpoint("unsafe-test-stub")
//                        .stubExecutionLayerManagerConstructor { serviceConfig, _ ->
//                            createStubExecutionManager(serviceConfig)
//                        }
                }

        if (validators.isNotEmpty()) {
            tekuConfigBuilder
                .validator {
                    it
                        .validatorKeys(listOf("$validatorKeysPath${File.pathSeparator}$validatorKeysPath"))
                        .validatorKeystoreLockingEnabled(false)
                        .proposerDefaultFeeRecipient("0x7777777777777777777777777777777777777777")
                }
        }

        val loggingConfig = LoggingConfig.builder()
            .logLevel(Level.DEBUG)
            .dataDirectory(dataPath)
            .colorEnabled(false)
            .destination(LoggingDestination.BOTH)
            .build()
        LoggingConfigurator.update(loggingConfig)

        return tekuConfigBuilder.build()

    }

    private fun createEnrFromTekuConfig(tekuConfig: TekuConfiguration): String {
        val privateKeyBytes = tekuConfig.network().privateKeySource.orElseThrow().orGeneratePrivateKeyBytes
        val privKey = unmarshalPrivateKey(privateKeyBytes.toArrayUnsafe())
        return EnrBuilder()
            .privateKey(privKey)
            .address(tekuConfig.network().advertisedIp, tekuConfig.network().advertisedPort)
            .build()
            .asEnr()
    }

    fun writeGenesis() {
        val executionPayloadHeader = DataStructureUtil(spec).randomExecutionPayloadHeader()
        val genesisTime: Long = System.currentTimeMillis() / 1000 + STARTUP_TIME_SECONDS
        val genesisStateBuilder = GenesisStateBuilder()
            .spec(spec)
            .executionPayloadHeader(executionPayloadHeader)
            .genesisTime(genesisTime)

        validatorKeys.forEach {
            genesisStateBuilder.addValidator(it, validatorDepositAmount)
        }
        val genesisState = genesisStateBuilder.build()
        File(genesisFile).writeBytes(genesisState.sszSerialize().toArrayUnsafe())
    }

    fun writeValidatorKeys(validators: List<BLSKeyPair>, validatorKeysPath: String) {
        File(validatorKeysPath).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val keystoreWriter =
            EncryptedKeystoreWriter(random, validatorKeyPass, "qqq", Path.of(validatorKeysPath)) {
                println("[EncryptedKeystoreWriter] $it")
            }
        validators.forEach { keyPair ->
            keystoreWriter.writeValidatorKey(keyPair)
            val validatorPasswordFileName: String = keyPair.getPublicKey().toAbbreviatedString() + "_validator.txt"
            val validatorPasswordFile = Files.createFile(Path.of(validatorKeysPath).resolve(validatorPasswordFileName))
            Files.write(validatorPasswordFile, validatorKeyPass.toByteArray(Charset.defaultCharset()))
        }

    }

    fun generatePrivateKeys(cnt: Int, random: SecureRandom) =
        generateSequence { generateKeyPair(KeyType.SECP256K1, random = random) }
            .map { it.first }
            .take(cnt)
            .toList()

    fun generatePrivateKeysFromSeed(cnt: Int, seed: Long) =
        generatePrivateKeys(cnt, SecureRandom(Bytes.ofUnsignedLong(seed).toArrayUnsafe()))

    fun generatePrivateKeyFromSeed(seed: Long) = generatePrivateKeysFromSeed(1, seed)[0]

    companion object {
        fun createDeterministicRandom() = SecureRandom.getInstance("SHA1PRNG")
            .also { it.setSeed(byteArrayOf()) }
    }
}