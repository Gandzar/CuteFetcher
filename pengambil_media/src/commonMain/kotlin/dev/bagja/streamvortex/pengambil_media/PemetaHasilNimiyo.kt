package dev.bagja.streamvortex.pengambil_media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Mengubah objek `result` dari scraper Nimiyo (JSON: title, thumbnail, author, duration,
 * downloads[{type, quality, url, ext, size}]) menjadi [HasilMedia]. Murni dan dapat diuji;
 * tidak melakukan jaringan. Field yang tidak ada tetap null (tidak ada nilai karangan).
 */
object PemetaHasilNimiyo {
    private val AudioExt = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus")
    private val GambarExt = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
    private val RegexTinggi = Regex("""(\d{3,4})\s*p\b""", RegexOption.IGNORE_CASE)
    private val RegexBitrate = Regex("""(\d{2,3})\s*(?:kbps|k\b)""", RegexOption.IGNORE_CASE)

    /** Lempar [KegagalanEkstraksi] (INTERNAL) bila JSON tidak bisa dibaca. */
    fun petakan(platform: Platform, mesin: String, urlSumber: String, jsonResult: String): HasilMedia {
        val akar = try {
            Json.parseToJsonElement(jsonResult).jsonObject
        } catch (e: Exception) {
            throw KegagalanEkstraksi(KategoriGagal.INTERNAL, "Format hasil mesin tidak dikenali.", e)
        }
        val unduhan = (akar["downloads"] as? JsonArray).orEmpty()
        val formats = unduhan.mapIndexedNotNull { i, el -> (el as? JsonObject)?.let { formatDari(i, it) } }

        val jenis = when {
            formats.isEmpty() -> JenisMedia.VIDEO
            formats.map { it.jenis }.distinct().size > 1 -> JenisMedia.CAMPURAN
            else -> formats.first().jenis
        }
        return HasilMedia(
            judul = akar.teks("title") ?: akar.teks("description") ?: "",
            deskripsi = akar.teks("description")?.takeIf { it != akar.teks("title") },
            thumbnail = akar.teks("thumbnail") ?: akar.teks("cover"),
            durasiDetik = durasi(akar["duration"]),
            penulis = akar.teks("author") ?: akar.teks("username") ?: akar.teks("uploader"),
            urlSumber = urlSumber,
            platform = platform,
            formatMedia = formats,
            jenisMedia = jenis,
            statusEkstraksi = if (formats.isEmpty()) StatusEkstraksi.GAGAL else StatusEkstraksi.BERHASIL,
            mesin = mesin,
        )
    }

    private fun formatDari(indeks: Int, o: JsonObject): FormatMedia? {
        val url = o.teks("url")?.takeIf { it.isNotBlank() } ?: return null
        val tipe = o.teks("type").orEmpty()
        val kualitas = o.teks("quality").orEmpty()
        val ekstensi = (o.teks("ext") ?: ekstensiDariUrl(url) ?: tebakEkstensi(tipe)).lowercase()
        val teksBantu = "$tipe $kualitas"
        val jenis = when {
            ekstensi in AudioExt || tipe.contains("audio", true) || tipe.contains("mp3", true) -> JenisMedia.AUDIO
            ekstensi in GambarExt || tipe.contains("image", true) || tipe.contains("photo", true) -> JenisMedia.GAMBAR
            else -> JenisMedia.VIDEO
        }
        return FormatMedia(
            id = indeks.toString(),
            url = url,
            jenis = jenis,
            ekstensi = ekstensi,
            tinggi = if (jenis == JenisMedia.VIDEO) RegexTinggi.find(teksBantu)?.groupValues?.get(1)?.toIntOrNull() else null,
            bitrateKbps = if (jenis == JenisMedia.AUDIO) RegexBitrate.find(teksBantu)?.groupValues?.get(1)?.toIntOrNull() else null,
            ukuranByte = (o["size"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
            perluResolusi = url.contains("_resolve:"),
            dataMentah = o.toString(),
        )
    }

    private fun ekstensiDariUrl(url: String): String? {
        if (url.contains("_resolve:")) return null
        val jalur = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val e = jalur.substringAfterLast('.', "")
        return e.takeIf { it.length in 2..4 && it.all { c -> c.isLetterOrDigit() } }
    }

    private fun tebakEkstensi(tipe: String): String = when {
        tipe.contains("mp3", true) || tipe.contains("audio", true) -> "mp3"
        tipe.contains("image", true) || tipe.contains("photo", true) -> "jpg"
        else -> "mp4"
    }

    private fun JsonObject.teks(kunci: String): String? =
        (this[kunci] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** Durasi dalam detik: angka murni, atau "mm:ss" / "hh:mm:ss". Selain itu null. */
    private fun durasi(el: JsonElement?): Long? {
        val t = (el as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        t.toDoubleOrNull()?.let { return it.toLong().takeIf { d -> d >= 0 } }
        val bagian = t.split(':').map { it.toLongOrNull() ?: return null }
        if (bagian.size !in 2..3) return null
        return bagian.fold(0L) { acc, n -> acc * 60 + n }
    }
}
