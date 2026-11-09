package com.otokit.noharayh.ui.theme

import com.otokit.noharayh.ui.theme.chu.ChuXVerseXTheme
import com.otokit.noharayh.ui.theme.mai.MaiCirclePlusTheme

/**
 * 纯主题注册表：唯一登记稳定 ID、展示名、所属音游与主题描述。
 *
 * 只负责查询与合法性判断，不持有 Context、偏好、WebView、窗口或导航状态；
 * 不选择恢复来源，也不在初始化时加载背景。
 */
object ThemeRegistry {

    private const val MAI_CIRCLE_PLUS_ID: String = "mai-circle-plus"
    private const val CHU_XVERSEX_ID: String = "chu-xversex"

    private val themes: List<RegisteredTheme> = listOf(
        RegisteredTheme(
            id = MAI_CIRCLE_PLUS_ID,
            displayName = "CiRCLE+",
            gameLine = GameLine.Mai,
            definition = MaiCirclePlusTheme.definition,
        ),
        RegisteredTheme(
            id = CHU_XVERSEX_ID,
            displayName = "X-Verse-X",
            gameLine = GameLine.Chu,
            definition = ChuXVerseXTheme.definition,
        ),
    )

    /** 每个音游的显式默认项，引用已登记的 ID。 */
    private val defaultIds: Map<GameLine, String> = mapOf(
        GameLine.Mai to MAI_CIRCLE_PLUS_ID,
        GameLine.Chu to CHU_XVERSEX_ID,
    )

    private val byId: Map<String, RegisteredTheme>

    init {
        validateThemes(themes, defaultIds)
        byId = themes.associateBy { it.id }
    }

    /** 按规范 ID 精确查询；空、空白、未知或旧 ID 返回 null，不给默认项。 */
    fun findById(id: String?): RegisteredTheme? = id?.let(byId::get)

    /** 指定音游的登记项，顺序稳定；顺序不承担默认或持久化语义。 */
    fun themesFor(gameLine: GameLine): List<RegisteredTheme> =
        themes.filter { it.gameLine == gameLine }

    /** 指定音游的显式默认项。 */
    fun defaultFor(gameLine: GameLine): RegisteredTheme {
        val id = defaultIds[gameLine] ?: error("音游 $gameLine 缺少显式默认主题")
        return byId[id] ?: error("音游 $gameLine 的默认主题未登记：$id")
    }

    /**
     * 把一个候选 ID 规范化为该音游的合法主题。
     *
     * 只有此处会在未知或错属时回退到指定音游的默认项，绝不因 ID 属于另一音游而切换 gameLine。
     */
    fun resolveForGame(gameLine: GameLine, id: String?): RegisteredTheme {
        val found = findById(id)
        return if (found != null && found.gameLine == gameLine) found else defaultFor(gameLine)
    }
}

/**
 * 生产登记校验：ID 唯一、展示名与入口非空、每个音游有主题且默认项存在并归属正确。
 *
 * 配置错误直接失败并带可定位信息，不静默覆盖重复项、不退回列表首项。
 */
internal fun validateThemes(themes: List<RegisteredTheme>, defaultIds: Map<GameLine, String>) {
    require(themes.isNotEmpty()) { "主题登记为空" }

    val seenIds = HashSet<String>()
    for (theme in themes) {
        require(theme.id.isNotBlank()) { "存在空主题 ID" }
        require(seenIds.add(theme.id)) { "主题 ID 重复：${theme.id}" }
        require(theme.displayName.isNotBlank()) { "主题 ${theme.id} 缺少展示名" }
        requireValidHtmlAssetPath(theme)
    }

    for (gameLine in GameLine.entries) {
        require(themes.any { it.gameLine == gameLine }) { "音游 $gameLine 没有登记主题" }
        val defaultId = defaultIds[gameLine] ?: error("音游 $gameLine 缺少显式默认主题")
        val default = themes.firstOrNull { it.id == defaultId }
            ?: error("音游 $gameLine 的默认主题未登记：$defaultId")
        require(default.gameLine == gameLine) {
            "音游 $gameLine 的默认主题归属错误：$defaultId 属于 ${default.gameLine}"
        }
    }
}

private fun requireValidHtmlAssetPath(theme: RegisteredTheme) {
    val id = theme.id
    val path = theme.definition.htmlAssetPath
    require(path.isNotBlank()) { "主题 $id 的 HTML 入口为空" }
    require(!path.startsWith("/")) { "主题 $id 的 HTML 入口不得以 / 开头" }
    require(!path.contains('\\')) { "主题 $id 的 HTML 入口不得包含反斜杠" }
    require(!path.contains("://")) { "主题 $id 的 HTML 入口不得是完整 URL" }
    require(!path.startsWith("assets/")) { "主题 $id 的 HTML 入口不带 assets/ 前缀" }
    require(path.split('/').none { it == ".." || it == "." }) {
        "主题 $id 的 HTML 入口不得包含上级或当前目录段"
    }
}
