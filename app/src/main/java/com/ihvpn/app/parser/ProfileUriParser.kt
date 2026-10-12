package com.ihvpn.app.parser

import android.util.Base64
import com.ihvpn.app.model.ProtocolType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder

/**
 * Gerçek profil linki ayrıştırıcı.
 *
 * Desteklenen şemalar: vless://, vmess://, trojan://, ss://
 * vmess:// hariç hepsi düz URI formatında; vmess:// base64 ile kodlanmış JSON taşır.
 *
 * Bu sınıf Android çerçevesine (Base64 hariç) bağımlı değildir, bu yüzden
 * saf Kotlin ortamında (örn. birim testinde) doğrudan çalıştırılabilir.
 */
data class ParsedProfile(
    val name: String,
    val protocol: ProtocolType,
    val address: String,
    val port: Int,
    val userId: String,          // uuid (vless/vmess) veya şifre (trojan/ss)
    val network: String,         // tcp, ws, grpc, h2, xhttp ...
    val security: String,        // tls, reality, none
    val path: String? = null,
    val hostHeader: String? = null,
    val sni: String? = null,
    val publicKey: String? = null,   // reality pbk
    val shortId: String? = null,     // reality sid
    val flow: String? = null,        // xtls-rprx-vision vb.
    val extraParams: Map<String, String> = emptyMap(),
    val isXhttp: Boolean = false,
    val rawUri: String
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(uri: String): Result<ParsedProfile> = runCatching {
            val trimmed = uri.trim()
            when {
                trimmed.startsWith("vless://") -> parseVless(trimmed)
                trimmed.startsWith("trojan://") -> parseTrojanLike(trimmed, ProtocolType.TROJAN)
                trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
                trimmed.startsWith("vmess://") -> parseVmess(trimmed)
                else -> throw IllegalArgumentException("Desteklenmeyen şema: $trimmed")
            }
        }

        private fun queryParams(uri: URI): Map<String, String> {
            val query = uri.rawQuery ?: return emptyMap()
            return query.split("&").mapNotNull {
                val idx = it.indexOf('=')
                if (idx == -1) null else {
                    val k = URLDecoder.decode(it.substring(0, idx), "UTF-8")
                    val v = URLDecoder.decode(it.substring(idx + 1), "UTF-8")
                    k to v
                }
            }.toMap()
        }

        private fun fragmentName(uri: URI, fallback: String): String =
            uri.rawFragment?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() } ?: fallback

        /** vless://uuid@host:port?type=xhttp&security=tls&path=...&host=...&sni=...&pbk=...&sid=...&flow=...#name */
        private fun parseVless(raw: String): ParsedProfile {
            val uri = URI(raw)
            val params = queryParams(uri)
            val network = params["type"] ?: "tcp"
            return ParsedProfile(
                name = fragmentName(uri, "VLESS ${uri.host}"),
                protocol = ProtocolType.VLESS,
                address = uri.host,
                port = if (uri.port == -1) 443 else uri.port,
                userId = uri.userInfo ?: "",
                network = network,
                security = params["security"] ?: "none",
                path = params["path"],
                hostHeader = params["host"],
                sni = params["sni"],
                publicKey = params["pbk"],
                shortId = params["sid"],
                flow = params["flow"],
                extraParams = params,
                isXhttp = network.equals("xhttp", ignoreCase = true) || params["mode"]?.contains("xhttp", true) == true,
                rawUri = raw
            )
        }

        /** trojan://password@host:port?...#name (aynı query şeması vless ile ortak) */
        private fun parseTrojanLike(raw: String, protocol: ProtocolType): ParsedProfile {
            val uri = URI(raw)
            val params = queryParams(uri)
            val network = params["type"] ?: "tcp"
            return ParsedProfile(
                name = fragmentName(uri, "${protocol.label} ${uri.host}"),
                protocol = protocol,
                address = uri.host,
                port = if (uri.port == -1) 443 else uri.port,
                userId = uri.userInfo ?: "",
                network = network,
                security = params["security"] ?: "tls",
                path = params["path"],
                hostHeader = params["host"],
                sni = params["sni"],
                extraParams = params,
                isXhttp = network.equals("xhttp", ignoreCase = true),
                rawUri = raw
            )
        }

        /** ss://base64(method:password)@host:port#name  VEYA  ss://method:password@host:port#name */
        private fun parseShadowsocks(raw: String): ParsedProfile {
            val uri = URI(raw)
            val userInfoRaw = uri.userInfo
            val decodedUserInfo = if (userInfoRaw != null && !userInfoRaw.contains(":")) {
                String(Base64.decode(userInfoRaw, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            } else userInfoRaw ?: ""
            val methodAndPass = decodedUserInfo.split(":", limit = 2)
            return ParsedProfile(
                name = fragmentName(uri, "SS ${uri.host}"),
                protocol = ProtocolType.SHADOWSOCKS,
                address = uri.host,
                port = if (uri.port == -1) 8388 else uri.port,
                userId = methodAndPass.getOrElse(1) { "" },
                network = "tcp",
                security = "none",
                extraParams = mapOf("method" to methodAndPass.getOrElse(0) { "aes-256-gcm" }),
                rawUri = raw
            )
        }

        /** vmess://base64(json). JSON alanları AndroidLibXrayLite/v2rayN standardına uyar (v,ps,add,port,id,aid,net,type,host,path,tls,sni). */
        private fun parseVmess(raw: String): ParsedProfile {
            val b64 = raw.removePrefix("vmess://")
            val jsonStr = String(Base64.decode(b64, Base64.DEFAULT))
            val obj = json.parseToJsonElement(jsonStr).let { it as JsonObject }
            fun field(key: String): String? = obj[key]?.jsonPrimitive?.content
            val network = field("net") ?: "tcp"
            return ParsedProfile(
                name = field("ps") ?: "VMess ${field("add")}",
                protocol = ProtocolType.VMESS,
                address = field("add") ?: "",
                port = field("port")?.toIntOrNull() ?: 443,
                userId = field("id") ?: "",
                network = network,
                security = field("tls") ?: "none",
                path = field("path"),
                hostHeader = field("host"),
                sni = field("sni") ?: field("host"),
                extraParams = mapOf("aid" to (field("aid") ?: "0"), "type" to (field("type") ?: "none")),
                isXhttp = network.equals("xhttp", ignoreCase = true),
                rawUri = raw
            )
        }
    }
}
