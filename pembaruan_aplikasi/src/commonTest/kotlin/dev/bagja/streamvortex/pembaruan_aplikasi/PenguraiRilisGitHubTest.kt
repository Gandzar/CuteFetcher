package dev.bagja.streamvortex.pembaruan_aplikasi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PenguraiRilisGitHubTest {
    private val repo = "Gandzar/CuteFetcher"
    private val sha = "a".repeat(64)

    private fun rilis(tag: String = "v0.2.0", aset: String, extra: String = "") =
        """{"tag_name":"$tag","html_url":"https://github.com/$repo/releases/tag/$tag","body":"Catatan",$extra"assets":[$aset]}"""

    private fun apk(nama: String, digest: String? = "sha256:$sha", url: String = "https://github.com/$repo/releases/download/v0.2.0/$nama"): String {
        val tambah = if (digest != null) ",\"digest\":\"$digest\"" else ""
        return "{\"name\":\"$nama\",\"size\":1234,\"browser_download_url\":\"$url\"$tambah}"
    }

    @Test fun kodeVersi() {
        assertEquals(100, PenguraiRilisGitHub.kodeVersiDari("0.1.0"))
        assertEquals(10203, PenguraiRilisGitHub.kodeVersiDari("v1.2.3"))
        assertEquals(10203, PenguraiRilisGitHub.kodeVersiDari("v1.2.3-beta"))
        assertNull(PenguraiRilisGitHub.kodeVersiDari("abc"))
        assertNull(PenguraiRilisGitHub.kodeVersiDari("1.2"))
    }

    @Test fun memilihApkSesuaiAbiDanDigest() {
        val j = rilis(aset = apk("CuteFetcher-0.2.0-x86_64-release.apk") + "," + apk("CuteFetcher-0.2.0-arm64-v8a-release.apk"))
        val c = assertNotNull(PenguraiRilisGitHub.urai(j, repo, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("CuteFetcher-0.2.0-arm64-v8a-release.apk", c.namaApk)
        assertEquals(200, c.versionCode)
        assertEquals(sha, c.sha256)
        assertEquals(sha, PenguraiRilisGitHub.keAppUpdate(c, sha).sha256)
    }

    @Test fun x86TidakSalahMemilihX86_64() {
        val j = rilis(aset = apk("CuteFetcher-0.2.0-x86_64-release.apk"))
        assertNull(PenguraiRilisGitHub.urai(j, repo, listOf("x86")))
    }

    @Test fun urlDiluarRepoDitolak() {
        val j = rilis(aset = apk("CuteFetcher-0.2.0-arm64-v8a-release.apk", url = "https://evil.example/a.apk"))
        assertNull(PenguraiRilisGitHub.urai(j, repo, listOf("arm64-v8a")))
    }

    @Test fun draftDanPrereleaseDilewati() {
        val aset = apk("CuteFetcher-0.2.0-arm64-v8a-release.apk")
        assertNull(PenguraiRilisGitHub.urai(rilis(aset = aset, extra = "\"draft\":true,"), repo, listOf("arm64-v8a")))
        assertNull(PenguraiRilisGitHub.urai(rilis(aset = aset, extra = "\"prerelease\":true,"), repo, listOf("arm64-v8a")))
    }

    @Test fun tanpaDigest_memakaiSums() {
        val j = rilis(aset = apk("CuteFetcher-0.2.0-arm64-v8a-release.apk", digest = null) + "," + apk("SHA256SUMS.txt", digest = null))
        val c = assertNotNull(PenguraiRilisGitHub.urai(j, repo, listOf("arm64-v8a")))
        assertNull(c.sha256)
        assertNotNull(c.urlSums)
        val sums = "$sha  CuteFetcher-0.2.0-arm64-v8a-release.apk\n${"b".repeat(64)}  lain.apk\n"
        assertEquals(sha, PenguraiRilisGitHub.sha256Dari(sums, c.namaApk))
        assertNull(PenguraiRilisGitHub.sha256Dari("rusak", c.namaApk))
    }

    @Test fun jsonRusak_null() {
        assertNull(PenguraiRilisGitHub.urai("bukan json", repo, listOf("arm64-v8a")))
    }
}
