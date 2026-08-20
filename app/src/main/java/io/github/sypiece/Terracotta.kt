package io.github.sypiece

import android.util.Log
import net.burningtnt.terracotta.TerracottaAndroidAPI
import org.json.JSONObject
import java.io.Reader

object Terracotta {
    private val listeners = mutableListOf<Listener>()
    private var thread = Thread(this::doThread)
    private var isRunning = false

    var metadata = TerracottaAndroidAPI.Metadata(null, 0, null)

    fun getState(): TerracottaState {
        val state = TerracottaAndroidAPI.getState()
        val obj = JSONObject(state)
        val index = obj.getInt("index")
        return when (obj.getString("state")) {
            "waiting" -> TerracottaState.Waiting(index)

            "host-scanning" -> TerracottaState.Host.Scanning(index)
            "host-starting" -> TerracottaState.Host.Starting(index, obj.getString("room"))
            "host-ok" -> {
                val jsonProfiles = obj.getJSONArray("profiles")
                val profiles = mutableListOf< TerracottaState.Profile>()
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
            "guest-ok" -> TerracottaState.Guest.OK(index)

            "exception" -> TerracottaState.Exception(index)

            else -> throw IllegalArgumentException("Unknown state: ${obj.getString("state")}")
        }
    }

    fun setWaiting() {
        TerracottaAndroidAPI.setWaiting()
    }

    fun setScanning(room: String? = null, player: String? = null) {
        TerracottaAndroidAPI.setScanning(room, player, listOf("https://terracotta.glavo.site/acebc7d8-1208-47fd-b212-d03ac49e36e0"))
    }

    fun setGuesting(room: String, player: String? = null): Boolean {
        return TerracottaAndroidAPI.setGuesting(room, player, listOf("https://terracotta.glavo.site/acebc7d8-1208-47fd-b212-d03ac49e36e0"))
    }

    fun collectLogs(): Reader {
        return TerracottaAndroidAPI.collectLogs()
    }

    private fun doThread() {
        var index = 0;
        while (isRunning) {
            val state = getState()
            if (state.index > index) {
                index = state.index
                listeners.forEach {
                    try {
                        it.onStateChange(state)
                    } catch (e: Throwable) {
                        Log.e("Terracotta", "Listener threw exception", e)
                        removeListener(it)
                    }
                }
            } else {
                Thread.sleep(1000)
            }
        }
    }

    fun interface Listener {
        fun onStateChange(newState: TerracottaState)
    }

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

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            isRunning = false
            thread.join(1000)
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
            override val index: Int
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
}