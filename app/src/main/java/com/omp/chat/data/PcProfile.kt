package com.omp.chat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * En registrert PC (Windows eller Linux) som OmpChat kan koble til.
 *
 * Token lagres i SecureStore (Android Keystore) per PC, ikke i klartekst.
 * [token] feltet holdes kun for bakoverkompatibilitet med eksisterende data.
 */
data class PcProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val token: String = "",
    val mac: String = "",
    val wolBroadcast: String = "255.255.255.255",
    val wolPort: Int = 9,
    val lastCwd: String = "",
    val lastSession: String = "",
    val favoriteSessions: List<String> = emptyList(),
    val autoConnect: Boolean = true,
    val favoriteModels: List<String> = emptyList(),
    val modelUsageCounts: Map<String, Int> = emptyMap(),
    val fallbackModel: String = "",
    val useDockerWol: Boolean = false,
    val dockerWolUrl: String = "",
    val dockerWolToken: String = "",
    /** LAN-adresse til PC-en (f.eks. http://192.168.50.159:7777). */
    val lanUrl: String = "",
    /** Tailscale-adresse til PC-en (f.eks. http://100.96.46.66:7777). */
    val tailscaleUrl: String = "",
    /** Andre adresser til samme PC (LAN/Tailscale/VPN) som kan brukes hvis [baseUrl] svikter. */
    val endpoints: List<String> = emptyList()
) {
    /** Alle kjente adresser, primæradressen først, uten duplikater. */
    fun candidateUrls(): List<String> {
        val all = buildList {
            add(baseUrl)
            if (lanUrl.isNotBlank()) add(lanUrl)
            if (tailscaleUrl.isNotBlank()) add(tailscaleUrl)
            addAll(endpoints)
        }
        return all
            .map { SettingsStore.normalizeBaseUrl(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }
}

class PcProfileStore(context: Context) {

    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): MutableList<PcProfile> {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val list = mutableListOf<PcProfile>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                // Les token fra SecureStore per PC, fall back til JSON-felt for migrering
                val secureToken = SecureStore(ctx, id).get()
                val token = secureToken ?: o.optString("token")
                val baseUrl = o.optString("baseUrl")
                val lanUrl = o.optString("lanUrl").ifBlank { baseUrl }
                val tailscaleUrl = o.optString("tailscaleUrl")
                val endpoints = (0 until o.optJSONArray("endpoints").let { it?.length() ?: 0 })
                    .mapNotNull { o.optJSONArray("endpoints")?.optString(it) }
                    .filter { it.isNotBlank() }
                // Migrer: hvis tailscaleUrl er blank men endpoints har en adresse,
                // anta at det er Tailscale-adressen
                val migratedTailscale = tailscaleUrl.ifBlank {
                    endpoints.firstOrNull { it.contains("100.") } ?: ""
                }
                list.add(
                    PcProfile(
                        id = id,
                        name = o.optString("name"),
                        baseUrl = baseUrl,
                        token = token,
                        mac = o.optString("mac"),
                        wolBroadcast = o.optString("wolBroadcast").ifBlank { "255.255.255.255" },
                        wolPort = o.optInt("wolPort", 9),
                        lastCwd = o.optString("lastCwd"),
                        lastSession = o.optString("lastSession"),
                        favoriteSessions = (0 until o.optJSONArray("favoriteSessions").let { it?.length() ?: 0 }).map { o.optJSONArray("favoriteSessions").optString(it) }.filter { it.isNotBlank() },
                        autoConnect = o.optBoolean("autoConnect", true),
                        favoriteModels = (0 until o.optJSONArray("favoriteModels").let { it?.length() ?: 0 }).map { o.optJSONArray("favoriteModels").optString(it) },
                        modelUsageCounts = run {
                            val obj = o.optJSONObject("modelUsageCounts")
                            val map = mutableMapOf<String, Int>()
                            obj?.keys()?.forEach { k -> map[k] = obj.optInt(k, 0) }
                            map
                        },
                        fallbackModel = o.optString("fallbackModel"),
                        useDockerWol = o.optBoolean("useDockerWol", false),
                        dockerWolUrl = o.optString("dockerWolUrl"),
                        dockerWolToken = o.optString("dockerWolToken"),
                        lanUrl = lanUrl,
                        tailscaleUrl = migratedTailscale,
                        endpoints = endpoints.filter { it != lanUrl && it != migratedTailscale }
                    )
                )
                // Migrer token til SecureStore hvis det kom fra JSON
                if (secureToken == null && token.isNotBlank()) {
                    SecureStore(ctx, id).put(token)
                }
            }
        }
        return list
    }

    fun save(profiles: List<PcProfile>) {
        val arr = JSONArray()
        for (p in profiles) {
            // Lagre token i SecureStore per PC
            if (p.token.isNotBlank()) {
                SecureStore(ctx, p.id).put(p.token)
            } else {
                SecureStore(ctx, p.id).clear()
            }
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                // Token lagres i SecureStore, ikke i JSON (men behold for bakoverkompatibilitet)
                put("token", p.token)
                put("mac", p.mac)
                put("wolBroadcast", p.wolBroadcast)
                put("wolPort", p.wolPort)
                put("lastCwd", p.lastCwd)
                put("lastSession", p.lastSession)
                val favSessionsArr = org.json.JSONArray()
                p.favoriteSessions.forEach { favSessionsArr.put(it) }
                put("favoriteSessions", favSessionsArr)
                put("autoConnect", p.autoConnect)
                val favArr = org.json.JSONArray()
                p.favoriteModels.forEach { favArr.put(it) }
                put("favoriteModels", favArr)
                val usageObj = org.json.JSONObject()
                p.modelUsageCounts.forEach { (k, v) -> usageObj.put(k, v) }
                put("modelUsageCounts", usageObj)
                put("fallbackModel", p.fallbackModel)
                put("useDockerWol", p.useDockerWol)
                put("dockerWolUrl", p.dockerWolUrl)
                put("dockerWolToken", p.dockerWolToken)
                put("lanUrl", p.lanUrl)
                put("tailscaleUrl", p.tailscaleUrl)
                val endpointsArr = org.json.JSONArray()
                p.endpoints.forEach { endpointsArr.put(it) }
                put("endpoints", endpointsArr)
            })
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun activeId(): String = prefs.getString(KEY_ACTIVE, "") ?: ""

    fun setActiveId(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_PROFILES).remove(KEY_ACTIVE).apply()
    }

    companion object {
        private const val PREFS = "omp_pc_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active"
    }
}

/** Sender en Wake-on-LAN magic packet (UDP) til broadcast-adressen. */
object WolSender {
    fun send(mac: String, broadcast: String = "255.255.255.255", port: Int = 9): Boolean {
        val clean = mac.replace(":", "").replace("-", "").replace(".", "").trim()
        if (clean.length != 12) return false
        if (!clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return false
        return try {
            val macBytes = ByteArray(6) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val packet = ByteArray(6 + 16 * 6)
            for (i in 0..5) packet[i] = 0xFF.toByte()
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
            }
            val addr = java.net.InetAddress.getByName(broadcast)
            val socket = java.net.DatagramSocket()
            socket.broadcast = true
            val dg = java.net.DatagramPacket(packet, packet.size, addr, port)
            socket.send(dg)
            socket.close()
            true
        } catch (e: Throwable) {
            false
        }
    }
}