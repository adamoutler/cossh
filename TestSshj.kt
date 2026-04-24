import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import java.util.Base64

fun main() {
    val pubKeyString = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl"
    val parts = pubKeyString.split(" ")
    val type = parts[0]
    val base64 = parts[1]
    val decoded = Base64.getDecoder().decode(base64)
    val buffer = Buffer.PlainBuffer(decoded)
    val pubKey = KeyType.fromString(type).readPubKeyFromBuffer(buffer)
    println(pubKey.algorithm)
}
