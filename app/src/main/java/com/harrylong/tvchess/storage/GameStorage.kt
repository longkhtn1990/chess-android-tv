package com.harrylong.tvchess.storage

import android.content.Context
import com.harrylong.tvchess.engine.ChessEngine
import com.harrylong.tvchess.model.*
import org.json.JSONArray
import org.json.JSONObject

data class SavedSession(
    val stateStack: List<GameState>,
    val playerColor: PieceColor,
    val difficulty: Difficulty,
    val soundEnabled: Boolean
)

object GameStorage {
    private const val PREF_NAME = "tv_chess_prefs"
    private const val KEY_SESSION = "saved_session_v1"
    private const val KEY_LESSON_PROGRESS = "lesson_progress_v1"
    private const val KEY_WIN_COUNT_PREFIX = "win_count_"
    private const val KEY_MUSIC_ENABLED = "music_enabled"
    private const val KEY_MUSIC_VOLUME = "music_volume"

    // =========================================================================
    // 1. QUẢN LÝ VÁN CỜ ĐANG CHƠI (SESSION)
    // =========================================================================

    fun hasSavedSession(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.contains(KEY_SESSION) && prefs.getString(KEY_SESSION, null) != null
        } catch (_: Throwable) {
            false
        }
    }

    fun clearSession(context: Context) {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SESSION)
                .apply()
        } catch (_: Throwable) {
        }
    }

    fun saveSession(
        context: Context,
        currentState: GameState,
        playerColor: PieceColor,
        difficulty: Difficulty,
        soundEnabled: Boolean
    ) {
        try {
            val root = JSONObject()
            root.put("playerColor", playerColor.name)
            root.put("difficulty", difficulty.name)
            root.put("soundEnabled", soundEnabled)

            val movesArray = JSONArray()
            for (move in currentState.moveHistory) {
                val mObj = JSONObject()
                mObj.put("fr", move.from.row)
                mObj.put("fc", move.from.col)
                mObj.put("tr", move.to.row)
                mObj.put("tc", move.to.col)
                if (move.promotionPiece != null) {
                    mObj.put("promo", move.promotionPiece.name)
                }
                movesArray.put(mObj)
            }
            root.put("moves", movesArray)

            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SESSION, root.toString())
                .apply()
        } catch (_: Throwable) {
        }
    }

    fun loadSession(context: Context): SavedSession? {
        try {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SESSION, null) ?: return null

            val root = JSONObject(raw)
            val playerColor = PieceColor.valueOf(root.optString("playerColor", PieceColor.WHITE.name))
            val difficulty = Difficulty.valueOf(root.optString("difficulty", Difficulty.MEDIUM.name))
            val soundEnabled = root.optBoolean("soundEnabled", true)

            val movesArray = root.optJSONArray("moves") ?: JSONArray()
            val stack = mutableListOf<GameState>()
            var state = ChessEngine.createInitialState()
            stack.add(state)

            for (i in 0 until movesArray.length()) {
                val mObj = movesArray.getJSONObject(i)
                val from = Position(mObj.getInt("fr"), mObj.getInt("fc"))
                val to = Position(mObj.getInt("tr"), mObj.getInt("tc"))
                val promoStr = mObj.optString("promo", "")
                val promoType = if (promoStr.isNotEmpty()) PieceType.valueOf(promoStr) else null

                val legalMoves = ChessEngine.getLegalMovesForPiece(state, from)
                val matchedMove = legalMoves.firstOrNull {
                    it.to == to && it.promotionPiece == promoType
                } ?: break

                state = ChessEngine.applyMove(state, matchedMove)
                stack.add(state)
            }

            return SavedSession(
                stateStack = stack,
                playerColor = playerColor,
                difficulty = difficulty,
                soundEnabled = soundEnabled
            )
        } catch (_: Throwable) {
            return null
        }
    }

    // =========================================================================
    // 2. TIẾN ĐỘ BÀI HỌC (LESSON PROGRESS: lessonId -> stars)
    // =========================================================================

    fun getLessonProgress(context: Context): Map<String, Int> {
        return try {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LESSON_PROGRESS, null) ?: return emptyMap()
            val json = JSONObject(raw)
            val result = mutableMapOf<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = json.optInt(key, 0)
            }
            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    fun getLessonStars(context: Context, lessonId: String): Int {
        return getLessonProgress(context)[lessonId] ?: 0
    }

    fun saveLessonStars(context: Context, lessonId: String, stars: Int) {
        try {
            val progress = getLessonProgress(context).toMutableMap()
            val currentStars = progress[lessonId] ?: 0
            if (stars > currentStars) {
                progress[lessonId] = stars
                val json = JSONObject()
                for ((id, s) in progress) {
                    json.put(id, s)
                }
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LESSON_PROGRESS, json.toString())
                    .apply()
            }
        } catch (_: Throwable) {
        }
    }

    // =========================================================================
    // 3. THÀNH TÍCH THẮNG THEO ĐỘ KHÓ (WIN COUNTS PER DIFFICULTY)
    // =========================================================================

    fun getWinCount(context: Context, difficulty: Difficulty): Int {
        return try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_WIN_COUNT_PREFIX + difficulty.name, 0)
        } catch (_: Throwable) {
            0
        }
    }

    fun incrementWinCount(context: Context, difficulty: Difficulty): Int {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val current = prefs.getInt(KEY_WIN_COUNT_PREFIX + difficulty.name, 0)
            val next = current + 1
            prefs.edit().putInt(KEY_WIN_COUNT_PREFIX + difficulty.name, next).apply()
            next
        } catch (_: Throwable) {
            0
        }
    }

    fun setWinCount(context: Context, difficulty: Difficulty, count: Int) {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_WIN_COUNT_PREFIX + difficulty.name, count.coerceAtLeast(0))
                .apply()
        } catch (_: Throwable) {
        }
    }

    // =========================================================================
    // 4. CÀI ĐẶT NHẠC NỀN (BGM SETTINGS)
    // =========================================================================

    fun isMusicEnabled(context: Context): Boolean {
        return try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MUSIC_ENABLED, true)
        } catch (_: Throwable) {
            true
        }
    }

    fun setMusicEnabled(context: Context, enabled: Boolean) {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MUSIC_ENABLED, enabled)
                .apply()
        } catch (_: Throwable) {
        }
    }

    fun getMusicVolume(context: Context): Float {
        return try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(KEY_MUSIC_VOLUME, 0.4f)
        } catch (_: Throwable) {
            0.4f
        }
    }

    fun setMusicVolume(context: Context, volume: Float) {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_MUSIC_VOLUME, volume.coerceIn(0f, 1f))
                .apply()
        } catch (_: Throwable) {
        }
    }
}
