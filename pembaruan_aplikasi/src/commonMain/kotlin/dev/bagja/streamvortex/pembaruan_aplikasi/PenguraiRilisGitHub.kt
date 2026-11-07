package dev.bagja.streamvortex.pembaruan_aplikasi

import io.ymusic.remoteconfig.AppUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Rilis GitHub yang cocok untuk perangkat ini, sebelum sha256-nya dipastikan. */
data class CalonRilis(
    val versionCode: Int,
    val versionName: String,
    val catatan: String,
    val halamanRilis: String,
    val namaApk: String,
    val urlApk: String,
    val ukuran: Long,
    /** Dari field `digest` GitHub ("sha256:..."); null jika GitHub tidak menyediakannya. */
    val sha256: String?,
    /** URL berkas SHA256SUMS di rilis yang sama, jika ada (cadangan bila [sha256] null). */
    val urlSums: String?,
)

/**
 * Mengurai JSON `GET /repos/{repo}/releases/latest` menjadi [AppUpdate] model updater YMusic.
 * Keamanan: URL unduhan wajib berasal dari `https://github.com/{repo}/releases/download/`, sha256 wajib
 * valid (64 heksadesimal), dan pemasangan akhirnya tetap dijaga Android (tanda tangan APK harus sama).
 */
object PenguraiRilisGitHub {
    private const val BATAS_CATATAN = 4000
    private val RegexSha = Regex("^[0-9a-f]{64}$")

    /** "0.1.0" -> 100; "v1.2.3-beta" -> 10203. Null bila bukan angka.angka.angka. Harus sama dengan rumus di androidApp/build.gradle.kts. */
    fun kodeVersiDari(nama: String): Int? {
        val bagian = nama.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+').split('.')
        if (bagian.size != 3) return null
        val angka = bagian.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        if (angka[1] > 99 || angka[2] > 99) return null
        return angka[0] * 10_000 + angka[1] * 100 + angka[2]
    }

    /**
     * @param abis ABI perangkat, berurutan dari yang paling disukai (Build.SUPPORTED_ABIS).
     * @return null bila rilis tidak punya APK untuk ABI itu, versi tak terbaca, atau URL tak tepercaya.
     */
    fun urai(json: String, repo: String, abis: List<String>): CalonRilis? {
        val akar = try {
            Json.parseToJsonElement(json).jsonObject
        } catch (e: Exception) {
            return null
        }
        if (akar.boolean("draft") == true || akar.boolean("prerelease") == true) return null
        val tag = akar.teks("tag_name") ?: return null
        val kode = kodeVersiDari(tag) ?: return null
        val nama = tag.removePrefix("v").removePrefix("V")
        val aset = (akar["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val awalan = "https://github.com/$repo/releases/download/"

        val apk = abis.firstNotNullOfOrNull { abi ->
            aset.firstOrNull { a ->
                val n = a.teks("name").orEmpty()
                n.endsWith(".apk", ignoreCase = true) && "-${n.dropLast(4)}-".contains("-$abi-")
            }
        } ?: return null
        val urlApk = apk.teks("browser_download_url")?.takeIf { it.startsWith(awalan, ignoreCase = true) } ?: return null
        val ukuran = (apk["size"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val digest = apk.teks("digest")?.removePrefix("sha256:")?.lowercase()?.takeIf { RegexSha.matches(it) }
        val sums = aset.firstOrNull { it.teks("name").equals("SHA256SUMS.txt", ignoreCase = true) }
            ?.teks("browser_download_url")?.takeIf { it.startsWith(awalan, ignoreCase = true) }
        return CalonRilis(
            versionCode = kode,
            versionName = nama,
            catatan = (akar.teks("body") ?: "").take(BATAS_CATATAN),
            halamanRilis = akar.teks("html_url")?.takeIf { it.startsWith("https://github.com/$repo/", ignoreCase = true) } ?: "https://github.com/$repo/releases",
            namaApk = apk.teks("name").orEmpty(),
            urlApk = urlApk,
            ukuran = ukuran,
            sha256 = digest,
            urlSums = sums,
        )
    }

    /** Cari sha256 [namaApk] di teks SHA256SUMS ("<hex>  <nama>" per baris). */
    fun sha256Dari(sums: String, namaApk: String): String? =
        sums.lineSequence().mapNotNull { baris ->
            val bagian = baris.trim().split(Regex("\\s+"), limit = 2)
            if (bagian.size == 2 && bagian[1].trimStart('*') == namaApk) bagian[0].lowercase().takeIf { RegexSha.matches(it) } else null
        }.firstOrNull()

    fun keAppUpdate(c: CalonRilis, sha256: String): AppUpdate = AppUpdate(
        versionCode = c.versionCode,
        versionName = c.versionName,
        notes = c.catatan,
        url = c.urlApk,
        size = c.ukuran,
        sha256 = sha256,
        signature = "", // Android tidak memakainya: OS hanya memasang APK bertanda tangan sama.
        changelogUrl = c.halamanRilis,
        downloadPage = c.halamanRilis,
        installInApp = true,
    )

    private fun JsonObject.teks(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun JsonObject.boolean(k: String): Boolean? = (this[k] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
