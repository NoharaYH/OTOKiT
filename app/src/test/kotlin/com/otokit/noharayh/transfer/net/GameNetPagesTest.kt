package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.score.*
import com.otokit.noharayh.transfer.CaptureProtocolIssue
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class GameNetPagesTest {
    private class Session(val body: ByteArray) : NetSession {
        var postBody: String? = null
        override fun authorizationUrl(game: String) = error("Unused")
        override fun login(callback: URI, saveDiagnostic: (NetSession.Page) -> Unit) = error("Unused")
        override fun <T> read(uri: URI, post: String?, consume: (NetSession.Page) -> T): T {
            postBody = post ?: postBody
            return consume(NetSession.Page(uri, "text/html", (if (post == null) body else byteArrayOf()).inputStream(), 200))
        }
        override fun close() {}
    }

    @Test fun missingTokenPreservesOriginalResponseIncludingUnreadTail() {
        val bytes = ("<html>" + "中".repeat(400_000) + "</html>").toByteArray()
        val session = Session(bytes)
        var diagnostic: ByteArray? = null
        try {
            GameNetPages(ScoreGame.CHUNITHM).prepare(session, HtmlPageKind.ADVANCED) { diagnostic = it.stream.readBytes() }
            fail("Missing token must fail")
        } catch (error: NetProtocolException) {
            assertEquals(CaptureProtocolIssue.FORM_TOKEN_MISSING, error.issue)
        }
        assertArrayEquals(bytes, diagnostic)
        assertNull(session.postBody)
    }

    @Test fun tokenAttributeOrderAndEncodingAreAccepted() {
        val session = Session("<html><input value='a&b' name='token'></html>".toByteArray())
        GameNetPages(ScoreGame.CHUNITHM).prepare(session, HtmlPageKind.ADVANCED) { fail("Valid form rejected") }
        assertEquals("genre=99&token=a%26b", session.postBody)
    }

    @Test fun emptyTokenValueIsReportedSeparately() {
        val session = Session("<html><input name='token' value=''></html>".toByteArray())
        var preserved = false
        try {
            GameNetPages(ScoreGame.CHUNITHM).prepare(session, HtmlPageKind.BASIC) { preserved = true }
            fail("Empty token must fail")
        } catch (error: NetProtocolException) {
            assertEquals(CaptureProtocolIssue.FORM_TOKEN_VALUE_MISSING, error.issue)
        }
        assertTrue(preserved)
        assertNull(session.postBody)
    }

    @Test fun endpointTraceNeverCopiesArbitraryPathsOrQueryMaterial() {
        assertEquals("CALLBACK_CHUNITHM", NetTraceEndpoint.classify(URI("https://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/chunithm?code=private&state=secret")))
        assertEquals("OTHER_CHUNITHM", NetTraceEndpoint.classify(URI("https://chunithm.wahlap.com/private-secret?code=private")))
        assertEquals("CHUNITHM:record/musicGenre/sendAdvanced", NetTraceEndpoint.classify(URI("https://chunithm.wahlap.com/mobile/record/musicGenre/sendAdvanced")))
    }
}
