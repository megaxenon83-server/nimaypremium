package com.omp.chat.data

import android.util.Log
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URL

/**
 * Én adresse til en PC, testet mot `GET /api/ping`.
 *
 * [ok] betyr at bridgen svarte 200. [reason] er en kort, norsk forklaring som
 * kan vises direkte i UI – aldri en stille "feilet".
 */
data class EndpointProbe(
    val url: String,
    val ok: Boolean,
    val reason: String = "",
    val version: String = "",
    val hosts: List<String> = emptyList()
) {
    val host: String get() = runCatching { URI(url).host ?: url }.getOrDefault(url)
}

/**
 * Finner ut hvilken adresse til en PC som faktisk svarer.
 *
 * En PC kan nås på flere adresser (LAN, Tailscale, VPN). Bridgen svarer på
 * `/api/ping` uten token og rapporterer alle adressene den selv ser i `hosts`,
 * så appen kan lære nye ruter automatisk i stedet for å kreve Tailscale.
 */
object EndpointResolver {

    private const val TAG = "OmpChat"

    fun candidates(pc: PcProfile): List<String> = pc.candidateUrls()

    fun candidates(settings: SettingsStore): List<String> {
        val base = settings.baseUrl()
        if (base.isBlank()) return emptyList()
        return listOf(base)
    }

    /** Prober én adresse. Blokkerer – kall fra Dispatchers.IO. */
    fun probe(url: String, token: String? = null, timeoutMs: Int = 1800): EndpointProbe {
        val base = SettingsStore.normalizeBaseUrl(url)
        if (base.isBlank()) return EndpointProbe(url, false, "ugyldig adresse")
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$base/api/ping").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.useCaches = false
            // /api/ping er offentlig, men token sendes med så et svar også
            // bekrefter at tokenet er gyldig når bridgen krever det.
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val reason = when (code) {
                    401, 403 -> "token avvist (HTTP $code)"
                    else -> "HTTP $code"
                }
                return EndpointProbe(base, false, reason)
            }
            val json = runCatching { JSONObject(body) }.getOrNull()
            val hosts = json?.optJSONArray("hosts")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty()
            EndpointProbe(
                url = base,
                ok = true,
                reason = "svarte",
                version = json?.optString("version").orEmpty(),
                hosts = hosts
            )
        } catch (e: SocketTimeoutException) {
            EndpointProbe(base, false, "tidsavbrudd (${timeoutMs}ms)")
        } catch (e: ConnectException) {
            EndpointProbe(base, false, "tilkobling nektet")
        } catch (e: NoRouteToHostException) {
            EndpointProbe(base, false, "ingen rute")
        } catch (e: UnknownHostException) {
            EndpointProbe(base, false, "ukjent adresse")
        } catch (e: Exception) {
            EndpointProbe(base, false, e.javaClass.simpleName + ": " + (e.message ?: "ukjent feil"))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** Prober alle kjente adresser til PC-en, i prioritert rekkefølge. */
    fun probeAll(pc: PcProfile, timeoutMs: Int = 1800): List<EndpointProbe> =
        candidates(pc).map { probe(it, pc.token, timeoutMs) }

    /**
     * Prober adressene og returnerer den første som svarer, eller null.
     * Logger hvert forsøk slik at feil kan feilsøkes fra logcat.
     */
    fun firstReachable(pc: PcProfile, timeoutMs: Int = 1800): EndpointProbe? {
        for (candidate in candidates(pc)) {
            val result = probe(candidate, pc.token, timeoutMs)
            Log.d(TAG, "endpoint ${result.host}: ${if (result.ok) "OK" else result.reason}")
            if (result.ok) return result
        }
        return null
    }

    /** Kort visningsnavn for en adresse, f.eks. `192.168.50.3:7777`. */
    fun hostLabel(url: String): String = runCatching {
        val uri = URI(SettingsStore.normalizeBaseUrl(url))
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "${uri.host}$port"
    }.getOrDefault(url)

    /** Merge nye adresser (fra `/api/ping` -> `hosts`) inn i profilen. */    fun mergeHosts(pc: PcProfile, hosts: List<String>): PcProfile {
        if (hosts.isEmpty()) return pc
        val port = runCatching { URI(pc.baseUrl).port }.getOrNull()?.takeIf { it > 0 } ?: 7777
        val scheme = if (pc.baseUrl.startsWith("https://", true)) "https" else "http"
        val discovered = hosts.mapNotNull { rawHost ->
            val host = rawHost.trim()
                .removePrefix("http://").removePrefix("https://")
                .substringBefore('/').substringBefore(':')
            if (host.isBlank()) null else "$scheme://$host:$port"
        }
        val existing = pc.candidateUrls()
        val merged = (existing + discovered)
            .map { SettingsStore.normalizeBaseUrl(it) }
            .filter { it.isNotBlank() }
            .distinct()
        // baseUrl skal alltid være primæradressen, ikke flyttes av discovery.
        val primary = SettingsStore.normalizeBaseUrl(pc.baseUrl)
        val extras = merged.filter { it != primary }
        if (extras == pc.endpoints.map { SettingsStore.normalizeBaseUrl(it) }.filter { it.isNotBlank() }) return pc
        return pc.copy(endpoints = extras)
    }
}
