package com.otokit.noharayh.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRegistryTest {

    /** 两条规范登记项与来源基准一致：ID、展示名、归属、入口与颜色。 */
    @Test
    fun registersTwoCanonicalThemes() {
        val mai = ThemeRegistry.findById("mai-circle-plus")
        assertNotNull(mai)
        assertEquals("CiRCLE+", mai!!.displayName)
        assertEquals(GameLine.Mai, mai.gameLine)
        assertEquals("background/MaiCirclePlus/html/index.html", mai.definition.htmlAssetPath)
        assertEquals(0xFF477FE5.toInt(), mai.definition.colors.basicColor)
        assertEquals(0xFFCF9AFA.toInt(), mai.definition.colors.buttonColor)

        val chu = ThemeRegistry.findById("chu-xversex")
        assertNotNull(chu)
        assertEquals("X-Verse-X", chu!!.displayName)
        assertEquals(GameLine.Chu, chu.gameLine)
        assertEquals("background/ChuXVerseX/html/index.html", chu.definition.htmlAssetPath)
        assertEquals(0xFF6F8CFF.toInt(), chu.definition.colors.basicColor)
        assertEquals(0xFF6F8CFF.toInt(), chu.definition.colors.buttonColor)
    }

    /** 原始查询不替未知、空、旧 ID 或展示名回退到默认项。 */
    @Test
    fun findByIdRejectsUnknownAndLegacyValues() {
        val rejected = listOf(
            null,
            "",
            " ",
            "unknown",
            "mai_circle_plus",
            "chu_x_verse_x",
            "MaiCirclePlus",
            "CiRCLE+",
            "MAI-CIRCLE-PLUS",
        )

        for (value in rejected) {
            assertNull("不应命中：$value", ThemeRegistry.findById(value))
        }
    }

    /** 按音游列举只返回该音游的登记项，且 ID 全局唯一。 */
    @Test
    fun themesForReturnsOnlyRequestedGameLine() {
        val maiThemes = ThemeRegistry.themesFor(GameLine.Mai)
        val chuThemes = ThemeRegistry.themesFor(GameLine.Chu)

        assertEquals(12, maiThemes.size)
        assertEquals(5, chuThemes.size)
        assertTrue(maiThemes.all { it.gameLine == GameLine.Mai })
        assertTrue(chuThemes.all { it.gameLine == GameLine.Chu })

        val allIds = (maiThemes + chuThemes).map { it.id }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    /** 默认项来自显式配置，且归属正确。 */
    @Test
    fun defaultsMatchExplicitConfiguration() {
        val maiDefault = ThemeRegistry.defaultFor(GameLine.Mai)
        val chuDefault = ThemeRegistry.defaultFor(GameLine.Chu)

        assertEquals("mai-circle-plus", maiDefault.id)
        assertEquals(GameLine.Mai, maiDefault.gameLine)
        assertEquals("chu-xversex", chuDefault.id)
        assertEquals(GameLine.Chu, chuDefault.gameLine)
    }

    /** 同音游合法 ID 保持原项；未知、空与错属 ID 回退到指定音游默认项。 */
    @Test
    fun resolveForGameFallsBackWithinRequestedGameLine() {
        val maiDefault = ThemeRegistry.defaultFor(GameLine.Mai)
        val chuDefault = ThemeRegistry.defaultFor(GameLine.Chu)

        assertSame(maiDefault, ThemeRegistry.resolveForGame(GameLine.Mai, "mai-circle-plus"))
        assertSame(chuDefault, ThemeRegistry.resolveForGame(GameLine.Chu, "chu-xversex"))

        assertSame(maiDefault, ThemeRegistry.resolveForGame(GameLine.Mai, "chu-xversex"))
        assertSame(chuDefault, ThemeRegistry.resolveForGame(GameLine.Chu, "mai-circle-plus"))
        assertSame(maiDefault, ThemeRegistry.resolveForGame(GameLine.Mai, null))
        assertSame(maiDefault, ThemeRegistry.resolveForGame(GameLine.Mai, "unknown"))
        assertSame(chuDefault, ThemeRegistry.resolveForGame(GameLine.Chu, " "))
    }

    /** 查询不改变登记项与默认项。 */
    @Test
    fun queriesDoNotMutateRegistry() {
        val before = ThemeRegistry.themesFor(GameLine.Mai).first()

        ThemeRegistry.findById("unknown")
        ThemeRegistry.resolveForGame(GameLine.Chu, "mai-circle-plus")

        assertSame(before, ThemeRegistry.findById("mai-circle-plus"))
        assertEquals("mai-circle-plus", ThemeRegistry.defaultFor(GameLine.Mai).id)
    }

    /** 返回列表可以拒绝修改，也可以是可修改副本；两者都不能改变注册表。 */
    @Test
    fun returnedListCannotMutateRegistry() {
        val list = ThemeRegistry.themesFor(GameLine.Mai)
        val before = list.toList()
        val defaultBefore = ThemeRegistry.defaultFor(GameLine.Mai)

        if (list is MutableList<RegisteredTheme>) {
            val fake = theme("fake", GameLine.Mai)
            try {
                list.add(fake)
            } catch (_: UnsupportedOperationException) {
                // JVM 的只读或固定大小列表可能通过类型检查，但拒绝添加。
            }
            try {
                list[0] = fake
            } catch (_: UnsupportedOperationException) {
                // 替换须单独尝试：固定大小列表可能允许替换，只读列表可以拒绝。
            }
        }

        assertEquals(before, ThemeRegistry.themesFor(GameLine.Mai))
        assertSame(defaultBefore, ThemeRegistry.defaultFor(GameLine.Mai))
        assertNull(ThemeRegistry.findById("fake"))
    }

    /** 生产校验对合法登记不抛异常。 */
    @Test
    fun validateAcceptsWellFormedRegistration() {
        validateThemes(validThemes(), validDefaults())
    }

    /** 重复 ID 明确失败，不被索引静默覆盖。 */
    @Test
    fun validateRejectsDuplicateIds() {
        val themes = listOf(
            theme("dup", GameLine.Mai),
            theme("dup", GameLine.Chu),
        )

        assertThrows(IllegalArgumentException::class.java) {
            validateThemes(themes, mapOf(GameLine.Mai to "dup", GameLine.Chu to "dup"))
        }
    }

    /** 空登记、空字段与缺失展示名明确失败。 */
    @Test
    fun validateRejectsEmptyOrBlankEntries() {
        assertThrows(IllegalArgumentException::class.java) {
            validateThemes(emptyList(), emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateThemes(listOf(theme(" "), theme("chu", GameLine.Chu)), validDefaults())
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateThemes(
                listOf(theme("mai", displayName = " "), theme("chu", GameLine.Chu)),
                validDefaults(),
            )
        }
    }

    /** 缺少默认项、默认项未登记或默认项错属都明确失败。 */
    @Test
    fun validateRejectsInvalidDefaults() {
        assertThrows(IllegalStateException::class.java) {
            validateThemes(validThemes(), mapOf(GameLine.Mai to "mai"))
        }
        assertThrows(IllegalStateException::class.java) {
            validateThemes(validThemes(), mapOf(GameLine.Mai to "mai", GameLine.Chu to "missing"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateThemes(validThemes(), mapOf(GameLine.Mai to "chu", GameLine.Chu to "chu"))
        }
    }

    /** 音游没有任何登记项明确失败。 */
    @Test
    fun validateRejectsGameLineWithoutThemes() {
        assertThrows(IllegalArgumentException::class.java) {
            validateThemes(listOf(theme("mai", GameLine.Mai)), mapOf(GameLine.Mai to "mai", GameLine.Chu to "mai"))
        }
    }

    /** HTML 入口必须是资产相对路径：不接受绝对路径、反斜杠、URL、assets/ 前缀与上级目录。 */
    @Test
    fun validateRejectsInvalidHtmlPaths() {
        val invalidPaths = listOf(
            "",
            " ",
            "/background/MaiCirclePlus/html/index.html",
            "background\\MaiCirclePlus\\html\\index.html",
            "https://example.com/index.html",
            "assets/background/MaiCirclePlus/html/index.html",
            "background/../html/index.html",
            "background/./html/index.html",
        )

        for (path in invalidPaths) {
            assertThrows("应拒绝入口：$path", IllegalArgumentException::class.java) {
                validateThemes(
                    listOf(theme("mai", GameLine.Mai, htmlAssetPath = path), theme("chu", GameLine.Chu)),
                    validDefaults(),
                )
            }
        }
    }

    private fun validThemes(): List<RegisteredTheme> = listOf(
        theme("mai", GameLine.Mai),
        theme("chu", GameLine.Chu),
    )

    private fun validDefaults(): Map<GameLine, String> = mapOf(
        GameLine.Mai to "mai",
        GameLine.Chu to "chu",
    )

    private fun theme(
        id: String,
        gameLine: GameLine = GameLine.Mai,
        displayName: String = "测试主题",
        htmlAssetPath: String = "background/Test/html/index.html",
    ): RegisteredTheme = RegisteredTheme(
        id = id,
        displayName = displayName,
        gameLine = gameLine,
        definition = ThemeDefinition(
            htmlAssetPath = htmlAssetPath,
            colors = ThemeColors(basicColor = 0xFF000000.toInt(), buttonColor = 0xFF000000.toInt()),
        ),
    )
}
