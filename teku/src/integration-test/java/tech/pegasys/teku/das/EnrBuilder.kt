package tech.pegasys.teku.das

import com.google.common.base.Preconditions
import io.libp2p.core.crypto.PrivKey
import io.libp2p.crypto.keys.Secp256k1PrivateKey
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.crypto.SECP256K1
import org.apache.tuweni.units.bigints.UInt64
import org.ethereum.beacon.discovery.schema.EnrField
import org.ethereum.beacon.discovery.schema.IdentitySchema
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.ethereum.beacon.discovery.schema.NodeRecordFactory
import org.ethereum.beacon.discovery.util.Functions
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class EnrBuilder {
    private val nodeRecordFactory = NodeRecordFactory.DEFAULT
    private val fields: MutableMap<String, Any> = mutableMapOf()
    private var privateKey: SECP256K1.SecretKey? = null
    private var seq = UInt64.ONE

    constructor()

    constructor(nodeRecord: NodeRecord) {
        nodeRecord.forEachField { t, u -> fields[t] = u }
    }

    fun seq(seq: UInt64?): EnrBuilder = also {
        this.seq = seq
    }

    fun seq(seq: Int): EnrBuilder {
        return seq(UInt64.valueOf(seq.toLong()))
    }

    fun publicKey(publicKey: Bytes): EnrBuilder = also {
        fields[EnrField.PKEY_SECP256K1] = publicKey
    }

    fun privateKey(privateKey: PrivKey): EnrBuilder = also {
        privateKey(privateKey.toDiscV5SecretKey())
    }

    fun privateKey(privateKey: SECP256K1.SecretKey): EnrBuilder = also {
        this.privateKey = privateKey
        publicKey(Functions.deriveCompressedPublicKeyFromPrivate(privateKey))
    }

//    fun network(network: Eth2Network): EnrBuilder = also {
//        val enrForkId = network.spec().getInstantSpecNow().enrForkId
//        customField(DiscoveryNetwork.ETH2_ENR_FIELD, enrForkId.sszSerialize())
//    }

    fun attestSubnets(subnetBits: SszBitvector) {
        customField(DiscoveryNetwork.ATTESTATION_SUBNET_ENR_FIELD, subnetBits.sszSerialize())
    }

    fun attestSubnets(subnets: List<Int>, totalSubnets: Int = 64) {
        val bitVector = SszBitvectorSchema.create(totalSubnets.toLong()).ofBits(subnets)
        attestSubnets(bitVector)
    }

    fun syncSubnets(subnetBits: SszBitvector) {
        customField(DiscoveryNetwork.SYNC_COMMITTEE_SUBNET_ENR_FIELD, subnetBits.sszSerialize())
    }

    fun syncSubnets(subnets: List<Int>, totalSubnets: Int = 4) {
        val bitVector = SszBitvectorSchema.create(totalSubnets.toLong()).ofBits(subnets)
        syncSubnets(bitVector)
    }

    fun address(ipAddress: String, port: Int): EnrBuilder {
        return address(ipAddress, port, port)
    }

    fun address(ipAddress: String, udpPort: Int, tcpPort: Int): EnrBuilder = also {
        try {
            val inetAddress = InetAddress.getByName(ipAddress)
            address(inetAddress, udpPort, tcpPort)
        } catch (e: UnknownHostException) {
            throw IllegalArgumentException("Unable to resolve address: $ipAddress")
        }
    }

    fun customField(fieldName: String, value: Bytes): EnrBuilder = also {
        fields[fieldName] = value
    }

    fun address(
        inetAddress: InetAddress,
        udpPort: Int,
        newTcpPort: Int?
    ): EnrBuilder = also {
        val address = Bytes.wrap(inetAddress.address)
        val isIpV6 = inetAddress is Inet6Address
        fields[if (isIpV6) EnrField.IP_V6 else EnrField.IP_V4] = address
        fields[if (isIpV6) EnrField.UDP_V6 else EnrField.UDP] = udpPort
        newTcpPort?.also { tcpPort: Int ->
            fields[if (isIpV6) EnrField.TCP_V6 else EnrField.TCP] = tcpPort
        }
    }

    fun build(): NodeRecord {
        fields[EnrField.ID] = IdentitySchema.V4
        val nodeRecord = nodeRecordFactory.createFromValues(seq,
            fields.map { (k, v) -> EnrField(k, v) })
        privateKey?.also { privateKey ->
            nodeRecord.sign(
                privateKey
            )
        }
        Preconditions.checkArgument(
            nodeRecord.isValid,
            "Generated node record was not valid. Ensure all required fields were supplied"
        )
        return nodeRecord
    }

    private fun PrivKey.toDiscV5SecretKey(): SECP256K1.SecretKey {
        require(this is Secp256k1PrivateKey)
        val rawBytes = this.raw()
        require(rawBytes.size <= 33)
        val noSignRawBytes =
            if (rawBytes.size == 33)
                rawBytes.slice(1..32).toByteArray()
            else rawBytes
        return Functions.createSecretKey(Bytes32.leftPad(Bytes.wrap(noSignRawBytes)))
    }
}