package com.ihvpn.app.parser

import com.ihvpn.app.model.ProtocolType
import org.json.JSONArray
import org.json.JSONObject

/**
 * ParsedProfile'dan Xray-core'un beklediği config.json'u üretir.
 * (AndroidLibXrayLite / Xray-core, config'i JSON string olarak alır — bkz. XrayCoreController.start)
 *
 * org.json kullanıldı (Android SDK'da hazır gelir, ekstra bağımlılık gerekmez).
 */
object XrayConfigBuilder {

    fun build(profile: ParsedProfile, socksInboundPort: Int = 10808): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // TUN üzerinden gelen trafiğin düşeceği yerel SOCKS girişi.
        // (Gerçek projede tun2socks katmanı bu porta yazar; bu iskelet Xray tarafını hazırlar.)
        val inbounds = JSONArray().put(
            JSONObject()
                .put("tag", "socks-in")
                .put("port", socksInboundPort)
                .put("listen", "127.0.0.1")
                .put("protocol", "socks")
                .put("settings", JSONObject().put("udp", true))
        )
        root.put("inbounds", inbounds)

        val outbound = JSONObject().put("tag", "proxy").put("protocol", protocolTag(profile.protocol))
        outbound.put("settings", buildOutboundSettings(profile))
        outbound.put("streamSettings", buildStreamSettings(profile))

        val direct = JSONObject().put("tag", "direct").put("protocol", "freedom")
        val block = JSONObject().put("tag", "block").put("protocol", "blackhole")

        root.put("outbounds", JSONArray().put(outbound).put(direct).put(block))

        // Basit yönlendirme: özel/yerel adresler direct'e, geri kalan her şey proxy'ye.
        val routing = JSONObject().put("domainStrategy", "AsIs")
        val rules = JSONArray().put(
            JSONObject()
                .put("type", "field")
                .put("ip", JSONArray().put("geoip:private"))
                .put("outboundTag", "direct")
        )
        routing.put("rules", rules)
        root.put("routing", routing)

        // Xray-core'un dahili istatistik sayaçlarını açar (Ana Sayfa'daki gerçek hız/toplam veri için).
        root.put("stats", JSONObject())
        root.put("policy", JSONObject().put(
            "system", JSONObject()
                .put("statsOutboundUplink", true)
                .put("statsOutboundDownlink", true)
        ))

        return root.toString()
    }

    private fun protocolTag(protocol: ProtocolType): String = when (protocol) {
        ProtocolType.VLESS -> "vless"
        ProtocolType.VMESS -> "vmess"
        ProtocolType.TROJAN -> "trojan"
        ProtocolType.SHADOWSOCKS -> "shadowsocks"
        ProtocolType.UNKNOWN -> throw IllegalArgumentException("Bilinmeyen protokol, config üretilemez")
    }

    private fun buildOutboundSettings(p: ParsedProfile): JSONObject = when (p.protocol) {
        ProtocolType.VLESS -> JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", p.address).put("port", p.port).put("users", JSONArray().put(
                JSONObject().put("id", p.userId).put("encryption", "none")
                    .apply { p.flow?.let { put("flow", it) } }
            ))
        ))
        ProtocolType.VMESS -> JSONObject().put("vnext", JSONArray().put(
            JSONObject().put("address", p.address).put("port", p.port).put("users", JSONArray().put(
                JSONObject().put("id", p.userId).put("alterId", (p.extraParams["aid"] ?: "0").toInt()).put("security", "auto")
            ))
        ))
        ProtocolType.TROJAN -> JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", p.address).put("port", p.port).put("password", p.userId)
        ))
        ProtocolType.SHADOWSOCKS -> JSONObject().put("servers", JSONArray().put(
            JSONObject().put("address", p.address).put("port", p.port)
                .put("password", p.userId)
                .put("method", p.extraParams["method"] ?: "aes-256-gcm")
        ))
        ProtocolType.UNKNOWN -> JSONObject()
    }

    private fun buildStreamSettings(p: ParsedProfile): JSONObject {
        val stream = JSONObject().put("network", p.network)

        when {
            p.security.equals("reality", ignoreCase = true) -> {
                stream.put("security", "reality")
                stream.put("realitySettings", JSONObject()
                    .put("serverName", p.sni ?: p.address)
                    .put("publicKey", p.publicKey ?: "")
                    .put("shortId", p.shortId ?: "")
                    .put("fingerprint", "chrome")
                )
            }
            p.security.equals("tls", ignoreCase = true) -> {
                stream.put("security", "tls")
                stream.put("tlsSettings", JSONObject()
                    .put("serverName", p.sni ?: p.hostHeader ?: p.address)
                    .put("allowInsecure", false)
                )
            }
            else -> stream.put("security", "none")
        }

        when (p.network.lowercase()) {
            "ws" -> stream.put("wsSettings", JSONObject()
                .put("path", p.path ?: "/")
                .put("headers", JSONObject().apply { p.hostHeader?.let { put("Host", it) } })
            )
            "grpc" -> stream.put("grpcSettings", JSONObject()
                .put("serviceName", p.path?.trim('/') ?: "")
            )
            "httpupgrade" -> stream.put("httpupgradeSettings", JSONObject()
                .put("path", p.path ?: "/")
                .put("host", p.hostHeader ?: p.address)
            )
            "xhttp" -> {
                // Xray-core'a özgü xhttp transportu — sing-box'ın çözemediği kısım budur.
                val mode = p.extraParams["mode"] ?: "auto" // auto | packet-up | stream-up | stream-one
                stream.put("xhttpSettings", JSONObject()
                    .put("path", p.path ?: "/")
                    .put("host", p.hostHeader ?: p.address)
                    .put("mode", mode)
                )
            }
            "h2", "http" -> stream.put("httpSettings", JSONObject()
                .put("path", p.path ?: "/")
                .put("host", JSONArray().put(p.hostHeader ?: p.address))
            )
            "tcp" -> { /* ek ayar gerekmez */ }
        }

        return stream
    }
}
