package com.otokit.noharayh.score.html

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.util.Locale

/** NET HTML 导出副本的脱敏；不修改存档，也不关闭调用方的流。 */
internal object HtmlExportRedactor {
    private const val MASK = "[REDACTED]"
    private val identityNodes = listOf(
        ".name_block", ".friend_code_icon + div", ".friend_comment_block",
        ".player_name_in", ".user_data_friend_code .user_data_friend_tap span", ".player_team_data_space",
    ).joinToString(",")
    private val plainFormFields = setOf("idx", "diff", "genre", "playcheck", "search", "sort")
    private val sensitiveFields = setOf(
        "code", "state", "cookie", "authorization", "csrf", "xsrf", "session", "sessionid", "sessionkey", "sid", "jsessionid",
        "userid", "username", "playerid", "playername", "friendcode", "friendid", "nickname", "displayname",
        "openid", "unionid", "account", "accountid", "loginid", "loginname", "aimeid", "cardid", "email", "phone",
    )
    private val punctuation = Regex("[^a-z0-9]")
    private val queryValue = Regex("([?&#;])([^=&#;\\s]+)=([^&#;\\s]*)")

    fun write(source: InputStream, destination: OutputStream) {
        val document = Jsoup.parse(source, "UTF-8", "")
        document.outputSettings().prettyPrint(false).charset(Charsets.UTF_8)
        document.select(identityNodes).forEach { element ->
            element.text(MASK)
            element.attributes().asList().filter { it.key != "class" }.forEach { element.removeAttr(it.key) }
        }
        // 内联脚本、事件和注释可能重复携带凭据；静态成绩分析不依赖这些可执行内容。
        document.select("script").remove()
        document.getAllElements().forEach { element ->
            element.childNodes().filterIsInstance<Comment>().forEach { it.remove() }
            val field = element.attr("name").ifEmpty { element.id() }
            val hidden = element.normalName() == "input" && element.attr("type").equals("hidden", true)
            val privateField = sensitive(field) || element.attr("type").equals("password", true) ||
                (hidden && normalized(field) !in plainFormFields)
            if (privateField) {
                when (element.normalName()) {
                    "input" -> element.attr("value", MASK)
                    "textarea", "select" -> element.text(MASK)
                    "meta" -> element.attr("content", MASK)
                }
            }
            element.attributes().asList().forEach { attribute ->
                when {
                    attribute.key.startsWith("on", true) || attribute.key.equals("srcdoc", true) ->
                        element.removeAttr(attribute.key)
                    sensitive(attribute.key) -> element.attr(attribute.key, MASK)
                    attribute.value.trimStart().startsWith("javascript:", true) -> element.removeAttr(attribute.key)
                    else -> element.attr(attribute.key, redactQuery(attribute.value))
                }
            }
        }
        destination.write(document.outerHtml().toByteArray(Charsets.UTF_8))
    }

    private fun normalized(field: String): String = field.lowercase(Locale.ROOT).replace(punctuation, "").removePrefix("data")

    private fun sensitive(field: String): Boolean {
        val name = normalized(field)
        return name in sensitiveFields || name.endsWith("token") || name.endsWith("password") || name.endsWith("secret")
    }

    private fun redactQuery(value: String): String = queryValue.replace(value) { match ->
        val key = try {
            URLDecoder.decode(match.groupValues[2], "UTF-8")
        } catch (_: IllegalArgumentException) {
            // 外部 HTML 的非法参数编码无法确认其字段含义，因此不保留该参数的值。
            return@replace match.groupValues[1] + match.groupValues[2] + "=" + MASK
        }
        if (sensitive(key)) match.groupValues[1] + match.groupValues[2] + "=" + MASK else match.value
    }
}
