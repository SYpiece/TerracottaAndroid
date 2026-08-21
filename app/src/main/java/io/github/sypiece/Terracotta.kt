package io.github.sypiece

import android.util.Log
import net.burningtnt.terracotta.TerracottaAndroidAPI
import org.json.JSONObject
import java.io.Reader

object Terracotta {
    private val listeners = mutableListOf<Listener>()

    @Volatile
    private var thread = Thread(this::doThread)

    @Volatile
    private var isRunning = false

    @Volatile
    var metadata = TerracottaAndroidAPI.Metadata(null, 0, null)

    private var lastStateStr: String = ""

    private var lastState: TerracottaState = TerracottaState.Unknown(-1, "")

    @Synchronized
    fun hasNewState(): Boolean {
        return lastStateStr != TerracottaAndroidAPI.getState()
    }

    @Synchronized
    fun getState(): TerracottaState {
        if (!hasNewState()) {
            return lastState
        }
        val state = TerracottaAndroidAPI.getState()
        lastStateStr = state
        val obj = JSONObject(state)
        val index = obj.getInt("index")
        lastState = when (obj.getString("state")) {
            "waiting" -> TerracottaState.Waiting(index)

            "host-scanning" -> TerracottaState.Host.Scanning(index)
            "host-starting" -> TerracottaState.Host.Starting(index, obj.getString("room"))
            "host-ok" -> {
                val jsonProfiles = obj.getJSONArray("profiles")
                val profiles = mutableListOf<TerracottaState.Profile>()
                for (i in 0 until jsonProfiles.length()) {
                    val profile = jsonProfiles.getJSONObject(i)
                    profiles.add(TerracottaState.Profile(
                        kind = profile.getString("kind"),
                        machineID = profile.getString("machine_id"),
                        name = profile.getString("name"),
                        vendor = profile.getString("vendor")
                    ))
                }
                TerracottaState.Host.OK(index, obj.getInt("profile_index"), profiles, obj.getString("room"))
            }

            "guest-connecting" -> TerracottaState.Guest.Connecting(index, obj.getString("room"))
            "guest-starting" -> TerracottaState.Guest.Starting(index, obj.getString("room"), obj.getString("difficulty"))
            "guest-ok" -> {
                val jsonProfiles = obj.getJSONArray("profiles")
                val profiles = mutableListOf<TerracottaState.Profile>()
                for (i in 0 until jsonProfiles.length()) {
                    val profile = jsonProfiles.getJSONObject(i)
                    profiles.add(TerracottaState.Profile(
                        kind = profile.getString("kind"),
                        machineID = profile.getString("machine_id"),
                        name = profile.getString("name"),
                        vendor = profile.getString("vendor")
                    ))
                }
                TerracottaState.Guest.OK(index, obj.getInt("profile_index"), profiles, obj.getString("url"))
            }

            "exception" -> TerracottaState.Exception(index)

            else -> TerracottaState.Unknown(index, obj.getString("state"))
        }
        Log.d("Terracotta", "New state: $lastState JSON: $state")
        return lastState
    }

    fun setWaiting() {
        TerracottaAndroidAPI.setWaiting()
    }

    private val extraNodes = listOf("https://terracotta.glavo.site/acebc7d8-1208-47fd-b212-d03ac49e36e0")

    fun setScanning(room: String? = null, player: String? = null) {
        TerracottaAndroidAPI.setScanning(room, player, extraNodes)
    }

    fun setGuesting(room: String, player: String? = null): Boolean {
        return TerracottaAndroidAPI.setGuesting(room, player, extraNodes)
    }

    fun collectLogs(): Reader {
        return TerracottaAndroidAPI.collectLogs()
    }

    private fun doThread() {
        var lastState = getState()
        while (isRunning) {
            val state = getState()
            if (lastState.index != state.index) {
                lastState = state
                synchronized(this) {
                    listeners.forEach {
                        try {
                            it.onStateChange(state)
                        } catch (e: Throwable) {
                            Log.e("TerracottaAndroid", "Listener threw exception", e)
                            removeListener(it)
                        }
                    }
                }
            } else {
                Thread.sleep(100)
            }
        }
    }

    fun interface Listener {
        fun onStateChange(newState: TerracottaState)
    }

    @Synchronized
    fun addListener(listener: Listener) {
        if (listeners.contains(listener)) {
            return
        }
        if (listeners.isEmpty()) {
            isRunning = true
            thread.priority = Thread.MIN_PRIORITY
            thread.start()
        }
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            isRunning = false
            thread.join(500)
            thread = Thread(this::doThread)
        }
    }
}

sealed class TerracottaState(open val index: Int) {
    data class Waiting(
        override val index: Int
    ) : TerracottaState(index)

    sealed class Host(override val index: Int) : TerracottaState(index) {
        data class Scanning(
            override val index: Int
        ) : Host(index)
        data class Starting(
            override val index: Int,
            val room: String
        ) : Host(index)
        data class OK(
            override val index: Int,
            val profileIndex: Int,
            val profiles: List<Profile>,
            val room: String
        ) : Host(index)
    }

    sealed class Guest(override val index: Int) : TerracottaState(index) {
        data class Connecting(
            override val index: Int,
            val room: String
        ) : Guest(index)
        data class Starting(
            override val index: Int,
            val room: String,
            val difficulty: String
        ) : Guest(index)
        data class OK(
            override val index: Int,
            val profileIndex: Int,
            val profiles: List<Profile>,
            val url: String
        ) : Guest(index)
    }

    data class Exception(
        override val index: Int
    ) : TerracottaState(index)

    data class Profile(
        val kind: String,
        val machineID: String,
        val name: String,
        val vendor: String
    )

    data class Unknown(
        override val index: Int,
        val state: String
    ) : TerracottaState(index)
}