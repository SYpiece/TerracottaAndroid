package io.github.sypiece

import android.content.Context
import android.net.VpnService
import android.util.Log
import net.burningtnt.terracotta.TerracottaAndroidAPI
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object Terracotta {
    private val stateListeners = mutableListOf<StateListener>()

    @Volatile
    private var thread = Thread(this::doThread)

    @Volatile
    private var isRunning = false

    @Volatile
    var metadata = TerracottaAndroidAPI.Metadata(null, 0, null)

    private var lastStateStr: String = ""

    private var lastState: TerracottaState = TerracottaState.Unknown(-1)

    var vpnRequestListener: VpnRequestListener? = null
        @Synchronized
        set

    fun initialize(context: Context) {
        metadata = TerracottaAndroidAPI.initialize(context) @Synchronized {
            val vpnRequest = TerracottaAndroidAPI.getPendingVpnServiceRequest()
            if (vpnRequestListener == null) {
                vpnRequest.reject()
            } else {
                val builder = vpnRequestListener?.onVpnRequest()
                if (builder == null) {
                    vpnRequest.reject()
                } else {
                    val vpnInterface = vpnRequest.startVpnService(builder)
                    addStateListener { oldState, newState ->
                        if ((oldState is TerracottaState.Host || oldState is TerracottaState.Guest)
                            && (newState is TerracottaState.Waiting || newState is TerracottaState.Exception)) {
                            vpnInterface.close()
                        }
                    }
                }
            }
        }
    }

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

        fun praseProfiles(jsonProfiles: JSONArray): List<TerracottaState.Profile> {
            val profiles = mutableListOf<TerracottaState.Profile>()
            for (i in 0 until jsonProfiles.length()) {
                val profile = jsonProfiles.getJSONObject(i)
                profiles.add(TerracottaState.Profile(
                    kind = TerracottaState.Profile.Kind.valueOf(profile.getString("kind")),
                    machineID = profile.getString("machine_id"),
                    name = profile.getString("name"),
                    vendor = profile.getString("vendor")
                ))
            }
            return profiles
        }

        lastState = when (obj.getString("state")) {
            "waiting" -> TerracottaState.Waiting(index)

            "host-scanning" -> TerracottaState.Host.Scanning(index)
            "host-starting" -> TerracottaState.Host.Starting(index, obj.getString("room"))
            "host-ok" -> TerracottaState.Host.OK(
                index,
                obj.getInt("profile_index"),
                praseProfiles(obj.getJSONArray("profiles")),
                obj.getString("room")
            )

            "guest-connecting" -> TerracottaState.Guest.Connecting(
                index,
                obj.getString("room")
            )
            "guest-starting" -> TerracottaState.Guest.Starting(
                index,
                obj.getString("room"),
                TerracottaState.Guest.Starting.Difficulty.valueOf(obj.getString("difficulty"))
            )
            "guest-ok" -> TerracottaState.Guest.OK(
                index,
                obj.getInt("profile_index"),
                praseProfiles(obj.getJSONArray("profiles")),
                obj.getString("url")
            )

            "exception" -> TerracottaState.Exception(
                index,
                TerracottaState.Exception.Type.entries[obj.getInt("type")]
            )

            else -> throw IllegalArgumentException("Unknown state: ${obj.getString("state")}")
        }
        Log.i("Terracotta", "New state: $lastState JSON: $state")
        return lastState
    }

    fun setWaiting() {
        TerracottaAndroidAPI.setWaiting()
    }

//    private val extraNodes = listOf("https://terracotta.glavo.site/acebc7d8-1208-47fd-b212-d03ac49e36e0")
    private const val NODE_LIST_URL = "https://terracotta.glavo.site/nodes"

    private val extraNodes: List<String> by lazy {
        val url = URL(NODE_LIST_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.setRequestProperty("Accept", "application/json")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))

            val response = StringBuilder()
            var line: String? = null
            while ((line = reader.readLine()) != null) {
                response.append(line)
            }
            reader.close()
            inputStream.close()

            val jsonString = response.toString()
            
        }
    }

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
                synchronized(this) {
                    stateListeners.forEach {
                        try {
                            it.onStateChange(lastState, state)
                        } catch (e: Throwable) {
                            Log.e("TerracottaAndroid", "Listener threw exception", e)
                            removeStateListener(it)
                        }
                    }
                }
                lastState = state
            } else {
                Thread.sleep(100)
            }
        }
    }

    fun interface StateListener {
        fun onStateChange(oldState: TerracottaState, newState: TerracottaState)
    }

    fun interface VpnRequestListener {
        fun onVpnRequest(): VpnService.Builder?
    }

    @Synchronized
    fun addStateListener(stateListener: StateListener) {
        if (stateListeners.contains(stateListener)) {
            return
        }
        if (stateListeners.isEmpty()) {
            isRunning = true
            thread.priority = Thread.MIN_PRIORITY
            thread.start()
        }
        stateListeners.add(stateListener)
    }

    @Synchronized
    fun removeStateListener(stateListener: StateListener) {
        stateListeners.remove(stateListener)
        if (stateListeners.isEmpty()) {
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
            val difficulty: Difficulty
        ) : Guest(index) {
            enum class Difficulty {
                UNKNOWN,
                EASIEST,
                SIMPLE,
                MEDIUM,
                TOUGH
            }
        }
        data class OK(
            override val index: Int,
            val profileIndex: Int,
            val profiles: List<Profile>,
            val url: String
        ) : Guest(index)
    }

    data class Exception(
        override val index: Int,
        val type: Type
    ) : TerracottaState(index) {
        enum class Type {
            PING_HOST_FAIL,
            PING_HOST_RST,
            GUEST_ET_CRASH,
            HOST_ET_CRASH,
            PING_SERVER_RST,
            SCAFFOLDING_INVALID_RESPONSE
        }
    }

    data class Profile(
        val kind: Kind,
        val machineID: String,
        val name: String,
        val vendor: String
    ) {
        enum class Kind {
            HOST, GUEST, LOCAL
        }
    }

    data class Unknown(
        override val index: Int
    ) : TerracottaState(index)
}