package io.github.sypiece

import net.burningtnt.terracotta.TerracottaAndroidAPI
import org.json.JSONObject
import java.io.Reader

object Terracotta {
    private val listeners = mutableListOf<(TerracottaState) -> Unit>()
    private var thread = Thread(this::doThread)
    private var isRunning = false

    var metadata = TerracottaAndroidAPI.Metadata(null, 0, null)

    fun getState(): TerracottaState {
        val obj = JSONObject(TerracottaAndroidAPI.getState())
        val index = obj.getInt("index")
        return when (obj.getString("state")) {
            "waiting" -> TerracottaState.Waiting(index)

            "host-scanning" -> TerracottaState.Host.Scanning(index)
            "host-starting" -> TerracottaState.Host.Starting(index)
            "host-ok" -> TerracottaState.Host.OK(index, obj.getInt("profile_index"))

            "guest-connecting" -> TerracottaState.Guest.Connecting(index)
            "guest-starting" -> TerracottaState.Guest.Starting(index)
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
                listeners.forEach { it(state) }
            } else {
                Thread.yield()
            }
        }
    }

    fun addListener(listener: (TerracottaState) -> Unit) {
        if (listeners.contains(listener)) {
            return
        }
        if (listeners.isEmpty()) {
            isRunning = true
            thread.start()
        }
        listeners.add(listener)
    }

    fun removeListener(listener: (TerracottaState) -> Unit) {
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
            override val index: Int
        ) : Host(index)
        data class OK(
            override val index: Int,
            val profileIndex: Int
        ) : Host(index)
    }

    sealed class Guest(override val index: Int) : TerracottaState(index) {
        data class Connecting(
            override val index: Int
        ) : Guest(index)
        data class Starting(
            override val index: Int
        ) : Guest(index)
        data class OK(
            override val index: Int
        ) : Guest(index)
    }

    data class Exception(
        override val index: Int
    ) : TerracottaState(index)
}