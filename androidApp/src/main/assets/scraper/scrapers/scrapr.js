// Cute Fetcher: server tambahan yang diport dari Scrapr (coflyn/scrapr, MIT) ke lingkungan WebView.
// Scrapr berjalan di Node (axios + cheerio); di sini memakai scraperFetch (jembatan OkHttp) dan DOMParser.
// Hanya server yang murni HTTP yang diport. Server Scrapr yang butuh puppeteer (savetik, fdown, snapinsta)
// tidak bisa dijalankan di Android dan sengaja dilewati.
// Copyright (c) 2026 coflyn (MIT). Port: Cute Fetcher.
import { scraperFetch } from "./httpHelper.js";

const UA_MAC =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
const UA_WIN =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

/** Satu permintaan HTTP penuh: { status, headers, data(string) }. Melempar bila jaringan putus. */
async function http(opts, nama) {
  return scraperFetch({ ...opts, rawResponse: true, parseJson: false }, nama);
}

function header(h, nama) {
  if (!h) return undefined;
  const kunci = Object.keys(h).find((k) => k.toLowerCase() === nama.toLowerCase());
  return kunci ? h[kunci] : undefined;
}

/** "a=1; b=2" dari header Set-Cookie (array atau string). */
function kuki(h) {
  const sc = header(h, "set-cookie");
  const arr = Array.isArray(sc) ? sc : sc ? [sc] : [];
  return arr.map((c) => String(c).split(";")[0]).join("; ");
}

function gabung(a, b) {
  return [a, b].filter(Boolean).join("; ");
}

function dom(html) {
  return new DOMParser().parseFromString(html || "", "text/html");
}

function teks(el) {
  return (el?.textContent || "").trim();
}

function multipart(fields) {
  const batas = "----WebKitFormBoundary" + Math.random().toString(36).substring(2);
  let body = "";
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined && v !== null) {
      body += `--${batas}\r\nContent-Disposition: form-data; name="${k}"\r\n\r\n${v}\r\n`;
    }
  }
  body += `--${batas}--\r\n`;
  return { body, contentType: `multipart/form-data; boundary=${batas}` };
}

function dariBase64Utf8(b64) {
  const bin = atob(b64);
  return new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)));
}

function jsonAtauNull(data) {
  if (data && typeof data === "object") return data;
  try {
    return JSON.parse(String(data));
  } catch (_) {
    return null;
  }
}

// ---------------------------------------------------------------- Twitter/X: savetwt.com
export async function scrapeSaveTwt(url) {
  try {
    const bersih = url.trim().split("?")[0].replace(/https:\/\/(?:fixupx|fxtwitter|vxtwitter|nitter)\.com/g, "https://x.com");

    const r1 = await http({ url: "https://savetwt.com/", headers: { "User-Agent": UA_MAC } }, "SaveTWT");
    const c1 = kuki(r1.headers);
    const d1 = dom(r1.data);
    const csrf =
      d1.querySelector('input[name="_token"]')?.getAttribute("value") ||
      d1.querySelector('meta[name="csrf-token"]')?.getAttribute("content");
    if (!csrf) throw new Error(`SaveTWT: token CSRF tidak ditemukan (HTTP ${r1.status}).`);

    const form = new URLSearchParams({ _token: csrf, return_locale: "en", url: bersih }).toString();
    const r2 = await http(
      {
        url: "https://savetwt.com/download",
        method: "POST",
        data: form,
        headers: {
          Cookie: c1,
          "Content-Type": "application/x-www-form-urlencoded",
          Accept: "application/json",
          "X-Requested-With": "XMLHttpRequest",
          "User-Agent": UA_MAC,
          Referer: "https://savetwt.com/",
          Origin: "https://savetwt.com",
        },
      },
      "SaveTWT",
    );
    const j = jsonAtauNull(r2.data);
    if (!j || !j.redirect) throw new Error(j?.message || `SaveTWT tidak memberi tautan unduhan (HTTP ${r2.status}).`);

    let alih = j.redirect;
    if (alih.startsWith("/")) alih = "https://savetwt.com" + alih;
    const sesi = gabung(c1, kuki(r2.headers));

    const r3 = await http(
      { url: alih, headers: { Cookie: sesi || c1, "User-Agent": UA_MAC, Referer: "https://savetwt.com/" } },
      "SaveTWT",
    );
    const d3 = dom(r3.data);
    const downloads = [];
    d3.querySelectorAll("table tr, .result__quality__table tr").forEach((tr) => {
      const btn = tr.querySelector("a.result__download__button, a.btn, a[href*='dl.savetwt.com']");
      const href = btn?.getAttribute("href");
      if (href && href.startsWith("http")) {
        downloads.push({ quality: teks(tr.querySelector(".result__quality")) || teks(btn) || "Download", url: href });
      }
    });
    if (downloads.length === 0) throw new Error("SaveTWT: tidak ada tautan unduhan di halaman hasil.");
    return { status: true, result: { title: "Twitter Media", downloads } };
  } catch (e) {
    return { status: false, message: e?.message || "SaveTWT gagal." };
  }
}

// ---------------------------------------------------------------- Reddit: rapidsave.com
export async function scrapeRapidSave(url) {
  try {
    const cocok = String(url || "").match(/https?:\/\/[^\s]+/i);
    let target = cocok ? cocok[0] : String(url || "").trim();
    if (!/^https?:\/\//i.test(target)) target = "https://" + target;

    // Tautan pendek reddit.com/r/.../s/xxxx -> alamat kanonik lewat header Location (tanpa mengikuti redirect).
    if (target.includes("/s/")) {
      try {
        const r = await http(
          { url: target, followRedirects: false, headers: { "User-Agent": UA_WIN } },
          "RapidSave",
        );
        const lokasi = header(r.headers, "location");
        if (lokasi) target = lokasi;
      } catch (_) {}
    }

    const r = await http(
      {
        url: `https://rapidsave.com/info?url=${encodeURIComponent(target)}`,
        headers: { "User-Agent": UA_WIN, Referer: "https://rapidsave.com/" },
      },
      "RapidSave",
    );
    const html = typeof r.data === "string" ? r.data : "";
    const dl =
      html.match(/class="downloadbutton"[^>]*href="([^"]+)"/i) ||
      html.match(/href="([^"]*sd\.rapidsave\.com\/download\.php[^"]*)"/i) ||
      html.match(/href="([^"]*download[^"]*)"/i);
    if (!dl) {
      if (html.includes("Reddit is limiting")) throw new Error("RapidSave: Reddit membatasi akses (rate limit).");
      throw new Error(`RapidSave: tautan unduhan tidak ditemukan (HTTP ${r.status}).`);
    }

    let judul = "Reddit Video";
    const jm = html.match(/class="text-center">([^<]+)<\/p>/i) || html.match(/<h2>([\s\S]*?)<\/h2>/i);
    if (jm && jm[1].trim()) judul = jm[1].trim();
    else {
      const slug = target.match(/comments\/[^/]+\/([^/?#]+)/);
      if (slug) judul = decodeURIComponent(slug[1].replace(/_/g, " "));
    }
    const tm = html.match(/<img[^>]+src="([^">]*thumbs\.rapidsave\.com[^">]*)"/i);
    const video = dl[1].replace(/&amp;/g, "&");

    const downloads = [{ type: "video", quality: "HD Video (with Audio)", url: video }];
    try {
      const audio = new URL(video).searchParams.get("audio_url");
      if (audio && audio.startsWith("http")) downloads.push({ type: "audio", quality: "Audio Track", url: audio });
    } catch (_) {}
    return { status: true, result: { title: judul, thumbnail: tm ? tm[1] : "", type: "video", downloads } };
  } catch (e) {
    return { status: false, message: e?.message || "RapidSave gagal." };
  }
}

// ---------------------------------------------------------------- Spotify: spotimate.app (spotmate)
export async function scrapeSpotMate(url) {
  try {
    const bersih = url.trim().split("?")[0];
    const BASE = "https://spotimate.app";

    const r1 = await http({ url: BASE + "/en1", headers: { "User-Agent": UA_MAC, Accept: "*/*" } }, "SpotMate");
    let kukis = kuki(r1.headers);

    let token = "";
    try {
      const v = await http(
        {
          url: BASE + "/api/userverify",
          method: "POST",
          data: "url=" + encodeURIComponent(bersih),
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "User-Agent": UA_MAC,
            "X-Requested-With": "XMLHttpRequest",
            Referer: BASE + "/en1",
            Origin: BASE,
            Cookie: kukis,
          },
        },
        "SpotMate",
      );
      const vj = jsonAtauNull(v.data);
      if (vj?.token) token = vj.token;
      kukis = gabung(kukis, kuki(v.headers));
    } catch (_) {}

    const act = multipart({ url: bersih, cftoken: token });
    const r2 = await http(
      {
        url: BASE + "/action",
        method: "POST",
        data: act.body,
        headers: { "Content-Type": act.contentType, "User-Agent": UA_MAC, Referer: BASE + "/en1", Origin: BASE, Cookie: kukis },
      },
      "SpotMate",
    );
    kukis = gabung(kukis, kuki(r2.headers));
    const aj = jsonAtauNull(r2.data);
    if (!aj || aj.success === false) throw new Error(aj?.message || `SpotMate gagal (HTTP ${r2.status}).`);

    const d = dom(aj.html || "");
    const judulH = teks(d.querySelector("h2, h3"));
    const artisP = teks(d.querySelector('p.text-sm, p[class*="text-white"], .spotifymate-middle p'));
    const thumbImg = d.querySelector('img[class*="rounded"], .spotifymate img, img')?.getAttribute("src") || "";

    const tracks = [];
    d.querySelectorAll("form").forEach((form) => {
      const data = form.querySelector('input[name="data"]')?.getAttribute("value") || "";
      const tt =
        form.querySelector('input[name="token"]')?.getAttribute("value") ||
        form.querySelector('input[name="track_token"]')?.getAttribute("value") ||
        "";
      const base = form.querySelector('input[name="base"]')?.getAttribute("value") || bersih;
      if (!data && !tt) return;
      const info = {};
      let tid = "";
      if (data) {
        try {
          const dec = JSON.parse(dariBase64Utf8(data));
          info.title = dec.name || dec.title || "";
          info.artist = dec.artist || dec.singer || "";
          info.thumbnail = dec.cover || "";
          tid = dec.tid || "";
        } catch (_) {}
      }
      tracks.push({
        data,
        base,
        trackToken: tt,
        title: info.title || judulH || "Spotify Track",
        artist: info.artist || artisP || "",
        thumbnail: info.thumbnail || thumbImg,
        track_url: tid ? `https://open.spotify.com/track/${tid}` : undefined,
      });
    });
    if (tracks.length === 0) throw new Error("SpotMate: tidak ada lagu ditemukan.");

    const downloads = [];
    const t0 = tracks[0];
    if (t0.data && t0.trackToken) {
      try {
        const tm = multipart({ data: t0.data, base: t0.base || bersih, token: t0.trackToken });
        const r3 = await http(
          {
            url: BASE + "/action/track",
            method: "POST",
            data: tm.body,
            headers: { "Content-Type": tm.contentType, "User-Agent": UA_MAC, Referer: BASE + "/en1", Origin: BASE, Cookie: kukis },
          },
          "SpotMate",
        );
        const dj = jsonAtauNull(r3.data);
        const dlHtml = dj?.data || dj?.html || "";
        if (dlHtml) {
          dom(dlHtml)
            .querySelectorAll("a[href^='http']")
            .forEach((a) => {
              const href = a.getAttribute("href");
              const t = teks(a).toLowerCase();
              if (!href || href.includes("tunecable") || href.includes("premium") || t.includes("another song")) return;
              const cover = t.includes("cover") || href.includes("cover") || href.includes("scdn.co") || /\.(jpg|jpeg|png|webp)(\?.*)?$/i.test(href);
              downloads.push({ quality: cover ? "cover" : "320kbps", type: cover ? "photo" : "mp3", url: href });
            });
        }
      } catch (_) {}
    }
    if (downloads.length === 0) throw new Error("SpotMate: tautan unduhan lagu tidak diberikan server.");

    const banyak = tracks.length > 1;
    const judul = t0.artist ? `${t0.artist} - ${t0.title}` : t0.title || judulH || "Spotify Track";
    return {
      status: true,
      result: {
        title: banyak ? `${judul} (${tracks.length} lagu)` : judul,
        thumbnail: t0.thumbnail || thumbImg,
        type: "audio",
        downloads,
      },
    };
  } catch (e) {
    return { status: false, message: e?.message || "SpotMate gagal." };
  }
}

// Daftarkan ke registri mesin Nimiyo (window.nimiyoEngine dibuat oleh scrapers/index.js).
if (typeof window !== "undefined") {
  const E = (window.nimiyoEngine = window.nimiyoEngine || {});
  E.twitter = { ...(E.twitter || {}), savetwt: scrapeSaveTwt };
  E.spotify = { ...(E.spotify || {}), spotmate: scrapeSpotMate };
  E.reddit = { ...(E.reddit || {}), rapidsave: scrapeRapidSave, default: scrapeRapidSave };
}
