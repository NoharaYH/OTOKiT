package com.otokit.noharayh.logged.scoresync

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** 只处理本模块的格式和已知正文类型；未知正文拒绝脱敏导出，不回退原文。 */
internal object ScoreSyncLogRedactor {
    private const val MASK = "[REDACTED]"
    private val privateNames = setOf("code", "state", "authorization", "proxyauthorization", "cookie", "setcookie",
        "sid", "session", "sessionid", "sessionkey", "jsessionid", "openid", "unionid", "sub", "subject",
        "account", "accountid", "userid", "username", "playerid", "playername", "friendcode", "friendid",
        "profileid", "nickname", "displayname", "name", "email", "phone", "aimeid", "cardid")
    private val query = Regex("([?&#;])([^=&#;\\s]+)=([^&#;\\s]*)")
    private val bearer = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+")
    private val localMessages = setOf("ready", "Not found", "Callback does not match this session", "Callback already consumed")

    private fun sensitive(key: String): Boolean {
        val name = key.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").removePrefix("data")
        return name in privateNames || listOf("token", "password", "secret", "credential", "subject").any(name::contains)
    }

    fun json(value: JSONObject): JSONObject {
        val result = JSONObject()
        val header = value.has("name") && value.has("value")
        value.keys().forEach { key ->
            val item = value.get(key)
            result.put(key, when {
                header && key == "name" -> item
                header && key == "value" && sensitive(value.optString("name")) -> MASK
                sensitive(key) && !(key.equals("code", true) && item is Number) -> MASK
                item is JSONObject -> json(item)
                item is JSONArray -> array(item)
                item is String -> text(item)
                else -> item
            })
        }
        return result
    }

    private fun array(value: JSONArray): JSONArray = JSONArray().apply {
        for (index in 0 until value.length()) put(when (val item = value.get(index)) {
            is JSONObject -> json(item)
            is JSONArray -> array(item)
            is String -> text(item)
            else -> item
        })
    }

    private fun text(value: String, depth: Int = 0): String {
        val stripped = bearer.replace(value, "Bearer $MASK")
        return query.replace(stripped) { match ->
            val key = decode(match.groupValues[2])
            val raw = match.groupValues[3]
            val replacement = when {
                key == null || sensitive(key) -> MASK
                depth < 3 -> {
                    val decoded = decode(raw)
                    if (decoded != null && (decoded.contains('?') || decoded.contains('='))) {
                        val cleaned = text(decoded, depth + 1)
                        if (cleaned != decoded) {
                            if (decoded == raw) cleaned else URLEncoder.encode(cleaned, "UTF-8")
                        } else raw
                    } else raw
                }
                else -> raw
            }
            match.groupValues[1] + match.groupValues[2] + "=" + replacement
        }
    }

    private fun decode(value: String): String? = try { URLDecoder.decode(value, "UTF-8") }
    catch (_: IllegalArgumentException) { null }

    fun body(source: InputStream, descriptor: JSONObject, output: OutputStream,
             redactHtml: (InputStream, OutputStream) -> Unit) {
        val encoding = descriptor.optString("contentEncoding").lowercase(Locale.ROOT).trim()
        val decoded = when (encoding) {
            "", "identity" -> source
            "gzip", "x-gzip" -> GZIPInputStream(source)
            "deflate" -> InflaterInputStream(source)
            else -> throw IOException("Unsupported logged content encoding")
        }
        val input = PushbackInputStream(decoded, 512)
        val prefix = ByteArray(512)
        val count = input.read(prefix)
        if (count == -1) return
        input.unread(prefix, 0, count)
        val start = prefix.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val type = descriptor.optString("contentType").lowercase(Locale.ROOT)
        when {
            type.contains("html") || start.startsWith('<') -> redactHtml(input, output)
            type.contains("json") || start.startsWith('{') || start.startsWith('[') -> {
                val raw = input.bufferedReader(Charsets.UTF_8).readText()
                try {
                    val clean = if (raw.trimStart().startsWith('[')) array(JSONArray(raw)).toString() else json(JSONObject(raw)).toString()
                    output.write(clean.toByteArray(Charsets.UTF_8))
                } catch (error: JSONException) { throw IOException("Invalid JSON body cannot be redacted", error) }
            }
            type.contains("application/x-www-form-urlencoded") -> {
                val form = input.bufferedReader(Charsets.UTF_8).readText().split('&').joinToString("&") { pair ->
                    val at = pair.indexOf('=')
                    if (at < 0) pair else {
                        val key = decode(pair.substring(0, at))
                        val value = decode(pair.substring(at + 1))
                        pair.substring(0, at + 1) + when {
                            key == null || sensitive(key) -> URLEncoder.encode(MASK, "UTF-8")
                            value == null -> URLEncoder.encode(MASK, "UTF-8")
                            else -> URLEncoder.encode(text(value), "UTF-8")
                        }
                    }
                }
                output.write(form.toByteArray(Charsets.UTF_8))
            }
            type.startsWith("text/plain") -> {
                val raw = input.bufferedReader(Charsets.UTF_8).readText()
                if (raw !in localMessages) throw IOException("Unknown plain-text body cannot be redacted")
                output.write(raw.toByteArray(Charsets.UTF_8))
            }
            else -> throw IOException("Unsupported logged body type cannot be redacted")
        }
    }
}
