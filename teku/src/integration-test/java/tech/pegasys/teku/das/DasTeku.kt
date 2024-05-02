package tech.pegasys.teku.das

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.apache.logging.log4j.Level
import org.apache.tuweni.bytes.Bytes
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.bls.BLSKeyPair
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.EncryptedKeystoreWriter
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreaker
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub
import tech.pegasys.teku.infrastructure.logging.LoggingConfig
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator
import tech.pegasys.teku.infrastructure.logging.LoggingDestination
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.service.serviceutils.ServiceConfig
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.TestSpecFactory
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder
import tech.pegasys.teku.spec.networks.Eth2Network
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
//            lineaTeku.createGenesisIfRequired()
            dasTeku.resetWithNewGenesis()
            dasTeku.createAndStartBootNode(0, 0 until 64)
        }
    }
}

/*

class RunNode {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTeku().createNode(1, 32 until 64)
        }
    }
}

class RunClientNode {
    companion object {

        @JvmStatic
        fun main(vararg args: String) {
            LineaTeku().createNode(2, 48 until 64)
        }
    }
}
*/

class DasTeku(
    val validatorsCount: Int = 64,
    val genesisTime: Long = System.currentTimeMillis() / 1000,
    val spec: Spec = TestSpecFactory.createMinimalEip7594 {
//        it
//            .secondsPerSlot(1)
//            .slotsPerEpoch(1)
//            .eth1FollowDistance(1.toUInt64())
//            .altairBuilder {
//                it
//                    // TODO: can't change NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT
//                    .syncCommitteeSize(4)
//            }
    },
    val validatorDepositAmount: UInt64 = spec.genesisSpecConfig.maxEffectiveBalance * 100,
    val stateStorageMode: StateStorageMode = StateStorageMode.PRUNE,

    val workDir: String = "./work.dir/linea",
    val advertisedIp: String = "10.150.1.122",
) {

    val genesisFile = "$workDir/genesis.ssz"
    val validatorKeyPass = "1234"
    val bootnodeEnrFile = "$workDir/bootnode-enr.txt"

    val random = SecureRandom(byteArrayOf())
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
//        val enr = bootNode.getEnr()
//        File(bootnodeEnrFile).writeText(enr)

        val node = TekuFacade.startBeaconNode(bootNodeConfig)

//        return bootNodeConfig
    }

//    fun createNodeConfig(
//        number: Int,
//        validators: IntRange
//    ) {
//        val bootnodeEnr = File(bootnodeEnrFile).readText()
//        return createNodeConfig(number, validatorKeys.slice(validators), bootnodeEnr)
//    }

    fun createNodeConfig(
        number: Int,
        validators: List<BLSKeyPair>,
        bootnodeEnr: String?,
        consoleOn: Boolean = true,
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
                        .stubExecutionLayerManagerConstructor { serviceConfig, _ ->
                            createStubExecutionManager(serviceConfig)
                        }
                }

            if (validators.isNotEmpty()) {
                tekuConfigBuilder
                    .validator {
                        it
                            .validatorKeys(listOf("$validatorKeysPath;$validatorKeysPath"))
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

//    private fun createEnrFromTekuConfig(tekuConfig: TekuConfiguration) {
//
//        val privateKeyBytes = tekuConfig.network().privateKeySource.orElseThrow().orGeneratePrivateKeyBytes
//        val privKey = unmarshalPrivateKey(privateKeyBytes.toArrayUnsafe())
//        return EnrBuilder()
//            .privateKey(privKey)
//            .address(tekuConfig.network().advertisedIp, tekuConfig.network().advertisedPort)
//            .build()
//            .asEnr()
//    }

    private fun createStubExecutionManager(serviceConfig: ServiceConfig) =
        ExecutionLayerManagerStub(
            spec,
            serviceConfig.timeProvider,
            true,
            Optional.empty(),
            BuilderCircuitBreaker.NOOP
        )

    fun writeGenesis() {
        val genesisStateBuilder = GenesisStateBuilder()
            .spec(spec)
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

}