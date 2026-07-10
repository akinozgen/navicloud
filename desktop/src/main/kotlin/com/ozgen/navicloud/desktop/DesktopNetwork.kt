package com.ozgen.navicloud.desktop

import java.util.concurrent.TimeUnit

/**
 * En iyi çaba (best-effort) metered (ölçülü) bağlantı tespiti — "Sadece Wi-Fi'de
 * indir" ayarı için. Android'in ConnectivityManager karşılığı masaüstünde yok;
 * OS komutlarıyla yoklanır. TESPİT EDİLEMEZSE metered DEĞİL kabul edilir
 * (fail-open) → indirme asla yanlışlıkla bloklanmaz. Sonuç kısa süre cache'lenir.
 */
object DesktopNetwork {
    @Volatile private var cachedAt = 0L
    @Volatile private var cached = false

    fun isMetered(): Boolean {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 10_000) return cached
        val os = System.getProperty("os.name").orEmpty().lowercase()
        cached = runCatching {
            when {
                os.contains("win") -> detectWindows()
                os.contains("linux") -> detectLinux()
                else -> false // macOS vb.: güvenilir yok → fail-open
            }
        }.getOrDefault(false)
        cachedAt = now
        return cached
    }

    /** Windows: WinRT NetworkCostType (Fixed/Variable = metered) — PowerShell köprüsü. */
    private fun detectWindows(): Boolean {
        val out = run(
            listOf(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                "try { [Windows.Networking.Connectivity.NetworkInformation,Windows.Networking.Connectivity,ContentType=WindowsRuntime]" +
                    "::GetInternetConnectionProfile().GetConnectionCost().NetworkCostType } catch { 'Unknown' }",
            ),
        ).trim()
        return out.equals("Fixed", true) || out.equals("Variable", true)
    }

    /** Linux: NetworkManager metered bayrağı (nmcli). "yes"/"yes (guessed)" = metered. */
    private fun detectLinux(): Boolean {
        val out = run(listOf("nmcli", "-t", "-f", "GENERAL.METERED", "device", "show"))
        return out.lineSequence().any { it.substringAfter(':', "").trim().startsWith("yes") }
    }

    private fun run(cmd: List<String>): String {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        return try {
            val text = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); return "" }
            text
        } catch (_: Exception) {
            p.destroyForcibly(); ""
        }
    }
}
