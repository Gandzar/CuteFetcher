// Cute Fetcher: penghubung Kotlin <-> mesin scraper (JavaScript) di dalam WebView tersembunyi.
// Dasar: host.js Musikku/Nimiyo (GPL-3.0, lihat NOTICE.md); server tambahan dari Scrapr (MIT).
//
// Alur satu permintaan: deteksi platform -> coba SETIAP server di "rantaiServer" secara bergantian
// (masing-masing dibatasi waktu) -> berhenti di server pertama yang berhasil -> kirim hasil ke Kotlin.
// Semua langkah dicatat ke jurnal debug aplikasi lewat window.CuteNative.log().
import "./scrapers/index.js"; // memasang window.nimiyoEngine (server Nimiyo)
import "./scrapers/scrapr.js"; // menambah server hasil port Scrapr ke window.nimiyoEngine
import { resolveDownloadUrl } from "./resolver.js";

// Pemetaan domain -> platform
const pemetaanPlatform = {
  youtube: /(youtube\.com|youtu\.be)/i,
  tiktok: /(tiktok\.com)/i,
  instagram: /(instagram\.com)/i,
  twitter: /(twitter\.com|x\.com)/i,
  spotify: /(spotify\.com)/i,
  applemusic: /(music\.apple\.com)/i,
  soundcloud: /(soundcloud\.com|on\.soundcloud\.com)/i,
  facebook: /(facebook\.com|fb\.watch|fb\.com)/i,
  threads: /(threads\.(?:net|com))/i,
  pinterest: /(pinterest\.com|pin\.it)/i,
  bilibili: /(bilibili\.com|b23\.tv)/i,
  douyin: /(douyin\.com)/i,
  bandcamp: /(bandcamp\.com)/i,
  pixiv: /(pixiv\.net|pixiv\.me|pximg\.net)/i,
  rednote: /(rednote\.com|xiaohongshu\.com|xhslink\.com|xhslink\.cn)/i,
  reddit: /(reddit\.com|redd\.it)/i,
};

// Urutan server per platform. Tiap nama = SERVER BERBEDA (alias yang menjalankan server sama sudah dibuang
// supaya server yang sama tidak dicoba dua kali). Yang pertama paling diutamakan.
const rantaiServer = {
  tiktok: ["tiktokio", "snaptik", "ssstik", "tikdownloader"],
  instagram: ["snapsave", "indown", "direct"],
  facebook: ["snapsave"],
  spotify: ["soundloaders", "spotidown", "spotisaver", "spotmate"],
  soundcloud: ["forhub", "klickaud", "direct"],
  twitter: ["direct", "tweeload", "tvd", "savetwt"],
  youtube: ["auto", "gg", "mobi"],
  applemusic: ["aplmate"],
  pinterest: ["pindown"],
  threads: ["threadster"],
  bilibili: ["direct"],
  douyin: ["direct"],
  bandcamp: ["bandcampdownloader"],
  pixiv: ["direct"],
  rednote: ["direct"],
  reddit: ["rapidsave"],
};

const BATAS_SERVER_MS = 45000; // satu server yang macet tidak boleh menahan server berikutnya

function deteksiPlatform(url) {
  if (!url) return null;
  return Object.keys(pemetaanPlatform).find((k) => pemetaanPlatform[k].test(url)) || null;
}

// ---- jembatan ke Kotlin ----------------------------------------------------------------
function catat(tingkat, pesan) {
  try {
    window.CuteNative.log(tingkat, String(pesan));
  } catch (_) {}
}

function balas(idPermintaan, muatan) {
  window.CuteNative.onResult(idPermintaan, JSON.stringify(muatan));
}

window.addEventListener("error", (e) => catat("E", `JS error: ${e.message} @${e.filename}:${e.lineno}`));
window.addEventListener("unhandledrejection", (e) => catat("E", `Promise tak tertangani: ${e.reason?.message || e.reason}`));

// ---- menjalankan satu server --------------------------------------------------------------
function denganBatasWaktu(janji, ms, nama) {
  let pewaktu;
  const habis = new Promise((_, tolak) => {
    pewaktu = setTimeout(() => tolak(new Error(`${nama}: waktu habis (${ms / 1000} dtk)`)), ms);
  });
  return Promise.race([janji, habis]).finally(() => clearTimeout(pewaktu));
}

/** Coba satu server. Tidak pernah melempar: selalu mengembalikan { ok, hasil?, pesan?, ms }. */
async function cobaServer(platform, server, url) {
  const mulai = performance.now();
  try {
    const modul = window.nimiyoEngine?.[platform];
    const fungsi = modul?.[server] || modul?.default;
    if (!fungsi) return { ok: false, pesan: "server tidak ada di mesin", ms: 0 };
    const res = await denganBatasWaktu(Promise.resolve(fungsi(url)), BATAS_SERVER_MS, server);
    const ms = Math.round(performance.now() - mulai);
    if (res && res.status === true && res.result) return { ok: true, hasil: res.result, ms };
    return { ok: false, pesan: res?.message || "gagal tanpa pesan", ms };
  } catch (e) {
    return { ok: false, pesan: e?.message || String(e), ms: Math.round(performance.now() - mulai) };
  }
}

// ---- API yang dipanggil Kotlin ------------------------------------------------------------------
/** Ambil info + tautan unduhan: coba server satu per satu sampai ada yang berhasil. */
async function ambil(idPermintaan, url) {
  const platform = deteksiPlatform(url);
  if (!platform) return balas(idPermintaan, { ok: false, pesan: "Tautan belum didukung." });

  const daftar = rantaiServer[platform] || ["direct"];
  catat("I", `[${platform}] mulai, ${daftar.length} server: ${daftar.join(" > ")}`);
  const gagal = [];
  for (const server of daftar) {
    catat("D", `[${platform}/${server}] mencoba…`);
    const r = await cobaServer(platform, server, url);
    if (r.ok) {
      catat("I", `[${platform}/${server}] BERHASIL ${r.ms} ms, ${(r.hasil.downloads || []).length} tautan`);
      return balas(idPermintaan, { ok: true, platform, server, hasil: r.hasil });
    }
    catat("W", `[${platform}/${server}] gagal ${r.ms} ms: ${r.pesan}`);
    gagal.push(`${server}: ${r.pesan}`);
  }
  catat("E", `[${platform}] SEMUA ${daftar.length} server gagal`);
  balas(idPermintaan, { ok: false, pesan: gagal.join("\n") || "Semua server gagal." });
}

/** Diagnosa: jalankan SEMUA server (tidak berhenti di yang pertama berhasil) dan laporkan satu-satu. */
async function diagnosa(idPermintaan, url) {
  const platform = deteksiPlatform(url);
  if (!platform) return balas(idPermintaan, { ok: false, pesan: "Tautan belum didukung." });
  const daftar = rantaiServer[platform] || ["direct"];
  catat("I", `[diagnosa ${platform}] menguji ${daftar.length} server`);
  const hasilServer = [];
  for (const server of daftar) {
    const r = await cobaServer(platform, server, url);
    const ringkas = r.ok ? `${(r.hasil.downloads || []).length} tautan` : r.pesan;
    catat(r.ok ? "I" : "W", `[diagnosa ${platform}/${server}] ${r.ok ? "OK" : "GAGAL"} ${r.ms} ms: ${ringkas}`);
    hasilServer.push({ server, sukses: r.ok, ms: r.ms, pesan: ringkas });
  }
  balas(idPermintaan, { ok: true, platform, hasilServer });
}

/** Ubah tautan semu ("*_resolve:...") menjadi URL unduhan nyata. */
async function selesaikan(idPermintaan, itemJson) {
  try {
    const keluar = await resolveDownloadUrl(JSON.parse(itemJson));
    catat("I", "resolusi tautan berhasil");
    balas(idPermintaan, { ok: true, ...keluar });
  } catch (e) {
    catat("E", `resolusi gagal: ${e?.message || e}`);
    balas(idPermintaan, { ok: false, pesan: e?.message || String(e) });
  }
}

// Nama lama (scrape/resolve) dipertahankan supaya Kotlin tidak perlu berubah; nama baru: diagnosa.
window.CuteHost = { scrape: ambil, resolve: selesaikan, diagnose: diagnosa };
catat("I", `host siap: ${Object.keys(rantaiServer).length} platform`);
window.CuteNative.onReady(); // beri tahu Kotlin bahwa host siap
