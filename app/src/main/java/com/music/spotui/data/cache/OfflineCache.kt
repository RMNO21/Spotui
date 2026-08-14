package com.music.spotui.data.cache

import android.content.Context
import com.music.spotui.data.entity.AlbumsModel
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.data.entity.LibraryEntry
import com.music.spotui.data.entity.SongsModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent offline disk cache for Home feed, Library, Playlists, and Liked Songs.
 * Enables the app to load instantly offline with all user data available without internet.
 */
object OfflineCache {
    private const val PREFS_NAME = "OfflinePersistentCache"
    private const val KEY_HOME = "cached_home_feed"
    private const val KEY_LIBRARY = "cached_library"
    private const val KEY_LIKED_SONGS = "cached_liked_songs"
    private const val KEY_ALBUMS = "cached_albums"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Home Feed ──
    fun saveHomeFeed(context: Context, feed: HomeFeedModel) {
        runCatching {
            val json = JSONObject().apply {
                put("greeting", feed.greeting)
                val sectionsArr = JSONArray()
                feed.sections.forEach { section ->
                    val secObj = JSONObject().apply {
                        put("title", section.title)
                        val itemsArr = JSONArray()
                        section.items.forEach { item ->
                            val itemObj = JSONObject().apply {
                                put("name", item.name)
                                put("imageUrl", item.imageUrl)
                                when (item) {
                                    is HomeItem.Album -> {
                                        put("type", "album")
                                        put("subtitle", item.subtitle)
                                        put("artists", item.artists)
                                    }
                                    is HomeItem.Artist -> {
                                        put("type", "artist")
                                        put("id", item.id)
                                    }
                                    is HomeItem.Playlist -> {
                                        put("type", "playlist")
                                        put("subtitle", item.subtitle)
                                        put("id", item.id)
                                    }
                                }
                            }
                            itemsArr.put(itemObj)
                        }
                        put("items", itemsArr)
                    }
                    sectionsArr.put(secObj)
                }
                put("sections", sectionsArr)
            }
            prefs(context).edit().putString(KEY_HOME, json.toString()).apply()
        }
    }

    fun loadHomeFeed(context: Context): HomeFeedModel? {
        val raw = prefs(context).getString(KEY_HOME, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val greeting = json.optString("greeting")
            val sectionsArr = json.optJSONArray("sections") ?: return@runCatching null
            val sections = mutableListOf<HomeSection>()
            for (i in 0 until sectionsArr.length()) {
                val secObj = sectionsArr.getJSONObject(i)
                val title = secObj.optString("title")
                val itemsArr = secObj.optJSONArray("items") ?: continue
                val items = mutableListOf<HomeItem>()
                for (j in 0 until itemsArr.length()) {
                    val itemObj = itemsArr.getJSONObject(j)
                    val name = itemObj.optString("name")
                    val imageUrl = itemObj.optString("imageUrl")
                    val type = itemObj.optString("type")
                    when (type) {
                        "album" -> items.add(
                            HomeItem.Album(
                                name = name,
                                imageUrl = imageUrl,
                                subtitle = itemObj.optString("subtitle"),
                                artists = itemObj.optString("artists"),
                            )
                        )
                        "artist" -> items.add(
                            HomeItem.Artist(
                                name = name,
                                imageUrl = imageUrl,
                                id = itemObj.optString("id"),
                            )
                        )
                        "playlist" -> items.add(
                            HomeItem.Playlist(
                                name = name,
                                imageUrl = imageUrl,
                                subtitle = itemObj.optString("subtitle"),
                                id = itemObj.optString("id"),
                            )
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    sections.add(HomeSection(title, items))
                }
            }
            if (sections.isNotEmpty()) HomeFeedModel(greeting, sections) else null
        }.getOrNull()
    }

    // ── Library ──
    fun saveLibrary(context: Context, entries: List<LibraryEntry>) {
        runCatching {
            val arr = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject().apply {
                    put("spotifyId", entry.spotifyId)
                    put("name", entry.name)
                    put("subtitle", entry.subtitle)
                    put("coverUri", entry.coverUri)
                    put("isPlaylist", entry.isPlaylist)
                    put("artists", entry.artists)
                }
                arr.put(obj)
            }
            prefs(context).edit().putString(KEY_LIBRARY, arr.toString()).apply()
        }
    }

    fun loadLibrary(context: Context): List<LibraryEntry>? {
        val raw = prefs(context).getString(KEY_LIBRARY, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<LibraryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    LibraryEntry(
                        spotifyId = obj.optString("spotifyId"),
                        name = obj.optString("name"),
                        subtitle = obj.optString("subtitle"),
                        coverUri = obj.optString("coverUri"),
                        isPlaylist = obj.optBoolean("isPlaylist"),
                        artists = obj.optString("artists"),
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        }.getOrNull()
    }

    // ── Liked Songs ──
    fun saveLikedSongs(context: Context, songs: List<SongsModel>) {
        runCatching {
            val arr = JSONArray()
            songs.forEach { song ->
                arr.put(songToJson(song))
            }
            prefs(context).edit().putString(KEY_LIKED_SONGS, arr.toString()).apply()
        }
    }

    fun loadLikedSongs(context: Context): List<SongsModel>? {
        val raw = prefs(context).getString(KEY_LIKED_SONGS, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<SongsModel>()
            for (i in 0 until arr.length()) {
                parseSong(arr.getJSONObject(i))?.let { list.add(it) }
            }
            if (list.isNotEmpty()) list else null
        }.getOrNull()
    }

    // ── Albums ──
    fun saveAlbums(context: Context, albums: List<AlbumsModel>) {
        runCatching {
            val arr = JSONArray()
            albums.forEach { album ->
                val obj = JSONObject().apply {
                    put("id", album.id)
                    put("artists", album.artists)
                    put("coverUri", album.coverUri)
                    put("name", album.name)
                    put("time", album.time)
                    put("type", album.type)
                }
                arr.put(obj)
            }
            prefs(context).edit().putString(KEY_ALBUMS, arr.toString()).apply()
        }
    }

    fun loadAlbums(context: Context): List<AlbumsModel>? {
        val raw = prefs(context).getString(KEY_ALBUMS, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<AlbumsModel>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AlbumsModel(
                        id = obj.optInt("id"),
                        artists = obj.optString("artists"),
                        coverUri = obj.optString("coverUri"),
                        name = obj.optString("name"),
                        time = obj.optString("time"),
                        type = obj.optString("type"),
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        }.getOrNull()
    }

    // ── Playlists ──
    fun savePlaylist(context: Context, playlistId: String, songs: List<SongsModel>) {
        if (playlistId.isBlank()) return
        runCatching {
            val arr = JSONArray()
            songs.forEach { arr.put(songToJson(it)) }
            prefs(context).edit().putString("playlist_$playlistId", arr.toString()).apply()
        }
    }

    fun loadPlaylist(context: Context, playlistId: String): List<SongsModel>? {
        if (playlistId.isBlank()) return null
        val raw = prefs(context).getString("playlist_$playlistId", null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<SongsModel>()
            for (i in 0 until arr.length()) {
                parseSong(arr.getJSONObject(i))?.let { list.add(it) }
            }
            if (list.isNotEmpty()) list else null
        }.getOrNull()
    }

    private fun songToJson(song: SongsModel): JSONObject = JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("album", song.album)
        put("singer", song.singer)
        put("coverUri", song.coverUri)
        put("url", song.url)
        put("spotifyTrackId", song.spotifyTrackId)
        put("explicit", song.explicit)
        put("durationMs", song.durationMs)
    }

    private fun parseSong(obj: JSONObject): SongsModel? = runCatching {
        SongsModel(
            id = obj.getInt("id"),
            title = obj.getString("title"),
            album = obj.optString("album"),
            singer = obj.getString("singer"),
            coverUri = obj.optString("coverUri"),
            url = obj.getString("url"),
            spotifyTrackId = obj.optString("spotifyTrackId"),
            explicit = obj.optBoolean("explicit", false),
            durationMs = obj.optInt("durationMs", 0),
        )
    }.getOrNull()
}
