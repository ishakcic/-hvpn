package com.ihvpn.app.parser

/**
 * sing-box çekirdeği xhttp taşımasını desteklemiyor ("unknown transport type: xhttp").
 * Xray-core (AndroidLibXrayLite) desteklediği için bu sınıf artık bir "engel" değil,
 * sadece kullanıcıya doğru bilgi vermek için kullanılıyor (örn. "bu profil xhttp kullanıyor" rozeti).
 */
object XhttpDetector {
    fun requiresXray(profile: ParsedProfile): Boolean = profile.isXhttp

    /** Spec'teki Türkçe uyarı metni — sing-box'a geri dönülürse kullanılacak. */
    fun singBoxRejectionMessage(): String =
        "Bu profil xhttp taşıması kullanıyor.\nMevcut çekirdek xhttp desteklemiyor.\nXray-core ile devam edin veya bu profili atlayın."
}
