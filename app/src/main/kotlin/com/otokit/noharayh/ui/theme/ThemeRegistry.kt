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
        // 占位登记：用于先验证主题选单和滚动，不新增 assets。
        placeholder("mai-circle", "CiRCLE", GameLine.Mai, 0xFFE91E63.toInt(), 0xFFAD1457.toInt()),
        placeholder("mai-prism-plus", "Prism+", GameLine.Mai, 0xFF9C27B0.toInt(), 0xFF6A1B9A.toInt()),
        placeholder("mai-prism", "Prism", GameLine.Mai, 0xFF673AB7.toInt(), 0xFF4527A0.toInt()),
        placeholder("mai-buddies-plus", "Buddies+", GameLine.Mai, 0xFF3F51B5.toInt(), 0xFF283593.toInt()),
        placeholder("mai-buddies", "Buddies", GameLine.Mai, 0xFF2196F3.toInt(), 0xFF1565C0.toInt()),
        placeholder("mai-festival-plus", "Festival+", GameLine.Mai, 0xFF00BCD4.toInt(), 0xFF00838F.toInt()),
        placeholder("mai-festival", "Festival", GameLine.Mai, 0xFF009688.toInt(), 0xFF00695C.toInt()),
        placeholder("mai-universe", "Universe", GameLine.Mai, 0xFF8BC34A.toInt(), 0xFF558B2F.toInt()),
        placeholder("mai-splash-plus", "Splash+", GameLine.Mai, 0xFFFF9800.toInt(), 0xFFE65100.toInt()),
        placeholder("mai-splash", "Splash", GameLine.Mai, 0xFFFF5722.toInt(), 0xFFBF360C.toInt()),
        placeholder("mai-dx", "DX", GameLine.Mai, 0xFF795548.toInt(), 0xFF4E342E.toInt()),
        placeholder("chu-verse", "Verse", GameLine.Chu, 0xFF03A9F4.toInt(), 0xFF0277BD.toInt()),
        placeholder("chu-luminous", "Luminous", GameLine.Chu, 0xFFFFC107.toInt(), 0xFFFF8F00.toInt()),
        placeholder("chu-sun", "Sun", GameLine.Chu, 0xFFFFEB3B.toInt(), 0xFFF9A825.toInt()),
        placeholder("chu-future", "Future", GameLine.Chu, 0xFFCDDC39.toInt(), 0xFF9E9D24.toInt()),
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

    private fun placeholder(
        id: String,
        name: String,
        gameLine: GameLine,
        basicColor: Int,
        buttonColor: Int,
    ): RegisteredTheme = RegisteredTheme(
        id = id,
        displayName = name,
        gameLine = gameLine,
        definition = ThemeDefinition(
            htmlAssetPath = when (gameLine) {
                GameLine.Mai -> MaiCirclePlusTheme.definition.htmlAssetPath
                GameLine.Chu -> ChuXVerseXTheme.definition.htmlAssetPath
            },
            colors = ThemeColors(basicColor, buttonColor),
        ),
    )
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
