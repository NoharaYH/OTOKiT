package com.otokit.noharayh.musicData

import org.json.JSONObject
import java.io.InputStream
import java.text.Normalizer

/** Frozen metadata used for exact matching and embedding the referenced chart revision. */
internal class ScoreCatalog(source: InputStream) {
    internal data class Song(val id: Int, val data: String)
    internal data class WorldsEnd(val kanji: String, val star: Int)
    private data class Key(val title: String, val type: String, val difficulty: Int, val worldsEnd: WorldsEnd? = null)
    private val entries: Map<Key, List<Song>>
    val version: String

    init {
        val root = source.bufferedReader().use { JSONObject(it.readText()) }
        version = root.getString("version")
        val songs = root.getJSONArray("songs")
        val index = mutableMapOf<Key, MutableList<Song>>()
        for (i in 0 until songs.length()) {
            val item = songs.getJSONObject(i)
            val basic = item.getJSONObject("basic_info")
            val song = Song(basic.getInt("id"), item.toString())
            val title = keyTitle(basic.getString("title"))
            val charts = item.optJSONArray("charts")
            val worldsEnd = item.optJSONObject("worlds_end_info")
            val keys = when {
                charts != null -> (0 until charts.length()).map {
                    Key(title, basic.getString("type"), charts.getJSONObject(it).getInt("difficulty"))
                }
                worldsEnd != null -> listOf(Key(title, "WORLDS_END", 5,
                    WorldsEnd(worldsEnd.getString("kanji"), worldsEnd.getInt("star"))))
                else -> listOf(Key(title, "UTAGE", 5))
            }
            for (key in keys) {
                index.getOrPut(key) { mutableListOf() }.add(song)
            }
        }
        entries = index
    }

    fun find(title: String, type: String, difficulty: Int, worldsEnd: WorldsEnd? = null): Song? =
        entries[Key(keyTitle(title), type, difficulty, worldsEnd)]?.singleOrNull()

    private fun keyTitle(title: String) =
        Normalizer.normalize(title.trim(' ', '\r', '\n', '\t'), Normalizer.Form.NFC)

}
