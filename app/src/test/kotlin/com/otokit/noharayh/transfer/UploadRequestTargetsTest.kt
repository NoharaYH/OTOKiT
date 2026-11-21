package com.otokit.noharayh.transfer

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.RequestTarget
import com.otokit.noharayh.transfer.upload.UploadRequestTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UploadRequestTargetsTest {
    @Test fun defaultsKeepBothProvidersAndGamesSeparate() {
        val targets = listOf(
            Triple(OAuthProvider.DIVING_FISH, true, "/api/maimaidxprober/player/update_records_html"),
            Triple(OAuthProvider.DIVING_FISH, false, "/api/chunithmprober/player/update_records_html"),
            Triple(OAuthProvider.LXNS, true, "/api/v0/user/maimai/player/html"),
            Triple(OAuthProvider.LXNS, false, "/api/v0/user/chunithm/player/html"),
        )
        for ((provider, mai, path) in targets) {
            assertEquals(path, UploadRequestTargets.url(NetworkSnapshot.defaults(), provider, mai).encodedPath)
        }
    }

    @Test fun uploadOverridesCannotBeCleartextOrEmbedCredentials() {
        val target = RequestTarget.UPLOAD_LXNS_MAIMAI
        UploadRequestTargets.validate(target, "https://custom.example/upload")
        for (invalid in listOf("http://custom.example/upload", "https://user:secret@custom.example/upload",
            "https://custom.example/upload?token=secret", "https://custom.example/upload#secret")) {
            assertThrows(IllegalArgumentException::class.java) { UploadRequestTargets.validate(target, invalid) }
        }
    }

    @Test fun uploadOriginCanOnlyChangeTogetherWithItsAuthorizationIssuer() {
        val target = RequestTarget.UPLOAD_LXNS_MAIMAI
        val unrelatedUpload = NetworkSnapshot(urls = mapOf(target to "https://custom.example/upload"))
        assertThrows(IllegalArgumentException::class.java) {
            UploadRequestTargets.validateRoute(unrelatedUpload, OAuthProvider.LXNS, true)
        }
        UploadRequestTargets.validateRoute(NetworkSnapshot(urls = mapOf(target to "https://maimai.lxns.net/custom")),
            OAuthProvider.LXNS, true)
        val coherent = NetworkSnapshot(urls = mapOf(target to "https://custom.example/upload",
            RequestTarget.OAUTH_LXNS_ISSUER to "https://custom.example/oidc"))
        UploadRequestTargets.validateRoute(coherent, OAuthProvider.LXNS, true)
        assertThrows(IllegalArgumentException::class.java) {
            UploadRequestTargets.validateRoute(NetworkSnapshot(urls = mapOf(target to "https://custom.example:444/upload",
                RequestTarget.OAUTH_LXNS_ISSUER to "https://custom.example/oidc")), OAuthProvider.LXNS, true)
        }
    }
}
