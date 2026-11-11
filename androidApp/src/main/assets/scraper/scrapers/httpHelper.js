import { CapacitorHttp, getUserAgent } from "../utils/index.js";

/**
 * Gets the configured HTTP request timeout limit in milliseconds.
 * Defaults to 30000ms (30s) if unset or invalid.
 * @returns {number} Timeout in milliseconds
 */
export function getRequestTimeout() {
  try {
    if (typeof localStorage !== "undefined") {
      const nimiyoSettings = localStorage.getItem("nimiyo_settings");
      if (nimiyoSettings) {
        const parsed = JSON.parse(nimiyoSettings);
        if (parsed && !isNaN(parsed.timeout) && parsed.timeout >= 5) {
          return parsed.timeout * 1000;
        }
      }
      const customSec = parseInt(localStorage.getItem("nimiyo_request_timeout"), 10);
      if (!isNaN(customSec) && customSec >= 5 && customSec <= 180) {
        return customSec * 1000;
      }
    }
  } catch (_) {}
  return 30000;
}

/**
 * Safely parses response data as JSON, detecting HTML error pages (Cloudflare/Rate Limit blocks).
 * @param {any} data - Raw response data
 * @param {string} serverName - Server name for error contextualization
 * @returns {object} Parsed JSON object
 */
export function parseJsonResponse(data, serverName = "Server") {
  if (typeof data === "object" && data !== null) return data;
  if (typeof data === "string") {
    const trimmed = data.trim();
    if (trimmed.startsWith("<") || trimmed.startsWith("<!DOCTYPE")) {
      throw new Error(
        `${serverName} returned an HTML error page (blocked or rate-limited). Please try another server or check your network connection.`,
      );
    }
    try {
      return JSON.parse(trimmed);
    } catch (e) {
      throw new Error(`${serverName} returned an invalid response format.`);
    }
  }
  throw new Error(`${serverName} returned an empty response.`);
}

/**
 * Centralized HTTP request client wrapping CapacitorHttp.
 * Injects active User-Agent, handles configurable timeouts, and performs defensive JSON parsing.
 * @param {object} options - Request options (method, url, headers, data, params, responseType)
 * @param {string} serverName - Name of the target server for logging and error reporting
 * @returns {Promise<any>} Response data (parsed if JSON)
 */
export async function scraperFetch(options, serverName = "Server") {
  const method = (
    options.method || (options.data ? "POST" : "GET")
  ).toUpperCase();
  const headers = { ...options.headers };

  if (!headers["User-Agent"] && !headers["user-agent"]) {
    headers["User-Agent"] = getUserAgent();
  }

  let isHeaderSpoofing = true;
  let isBypassSsl = false;
  let isForceIpv4 = false;
  let dohMode = "off";

  try {
    if (typeof localStorage !== "undefined") {
      isHeaderSpoofing = localStorage.getItem("nimiyo_header_spoofing") !== "false";
      isBypassSsl = localStorage.getItem("nimiyo_bypass_ssl") === "true";
      isForceIpv4 = localStorage.getItem("nimiyo_force_ipv4") === "true";
      dohMode = localStorage.getItem("nimiyo_doh") || "off";
    }
  } catch (_) {}

  if (isHeaderSpoofing) {
    try {
      const parsedUrl = new URL(options.url);
      if (!headers["Referer"] && !headers["referer"]) {
        headers["Referer"] = `${parsedUrl.protocol}//${parsedUrl.hostname}/`;
      }
    } catch (_) {}
    if (!headers["Accept"] && !headers["accept"]) {
      headers["Accept"] =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8";
    }
    if (!headers["Accept-Language"] && !headers["accept-language"]) {
      headers["Accept-Language"] = "en-US,en;q=0.9,id;q=0.8";
    }
    if (!headers["Sec-Fetch-Dest"] && !headers["sec-fetch-dest"]) {
      headers["Sec-Fetch-Dest"] = "empty";
    }
    if (!headers["Sec-Fetch-Mode"] && !headers["sec-fetch-mode"]) {
      headers["Sec-Fetch-Mode"] = "cors";
    }
  }

  if (dohMode !== "off") {
    try {
      const parsedUrl = new URL(options.url);
      const hostname = parsedUrl.hostname;
      if (
        !/^\d+\.\d+\.\d+\.\d+$/.test(hostname) &&
        parsedUrl.protocol === "https:"
      ) {
        const dohEndpoint =
          dohMode === "google"
            ? `https://dns.google/resolve?name=${hostname}&type=A`
            : `https://cloudflare-dns.com/dns-query?name=${hostname}&type=A`;
        fetch(dohEndpoint, {
          headers: { Accept: "application/dns-json" },
          signal: AbortSignal.timeout(2000),
        }).catch(() => {});
      }
    } catch (_) {}
  }

  const httpConfig = {
    url: options.url,
    headers: headers,
    connectTimeout: getRequestTimeout(),
    readTimeout: getRequestTimeout(),
  };

  if (isBypassSsl) {
    httpConfig.disableSSLValidation = true;
  }
  if (isForceIpv4) {
    httpConfig.ipv4Only = true;
  }

  if (options.data !== undefined) httpConfig.data = options.data;
  if (options.params !== undefined) httpConfig.params = options.params;
  if (options.responseType !== undefined)
    httpConfig.responseType = options.responseType;

  let response;
  const invoke =
    typeof window !== "undefined"
      ? window.__TAURI__?.core?.invoke ||
        window.__TAURI_INTERNALS__?.invoke ||
        window.__TAURI__?.invoke
      : null;

  const shareBridge =
    typeof window !== "undefined"
      ? window.NimiyoShareBridge ||
        window.NimidzShareBridge ||
        window.NimiyoShareBridge
      : null;

  // Share overlay: use native Android bridge (NimiyoShareBridge) when CapacitorHttp is unavailable
  if (shareBridge?.httpRequestAsync) {
    let fetchUrl = options.url;
    if (options.params) {
      const q = new URLSearchParams(options.params).toString();
      if (q) fetchUrl += (fetchUrl.includes("?") ? "&" : "?") + q;
    }
    const bridgeOpts = {
      url: fetchUrl,
      method,
      headers,
      responseType: options.responseType,
    };
    // Cute Fetcher: izinkan scraper membaca header Location tanpa mengikuti redirect.
    if (options.followRedirects === false) bridgeOpts.noRedirect = true;
    if (options.data !== undefined)
      bridgeOpts.data =
        typeof options.data === "object"
          ? JSON.stringify(options.data)
          : String(options.data);

    const reqId =
      "req_" + Math.random().toString(36).substring(2, 11) + "_" + Date.now();
    const raw = await new Promise((resolve) => {
      if (typeof window !== "undefined") {
        if (!window.__nimiyoShareCallbacks) window.__nimiyoShareCallbacks = {};
        window.__nimiyoShareCallbacks[reqId] = resolve;
        if (!window.__NimiyoShareCallbacks) window.__NimiyoShareCallbacks = {};
        window.__NimiyoShareCallbacks[reqId] = resolve;
      }
      shareBridge.httpRequestAsync(
        JSON.stringify(bridgeOpts),
        reqId,
      );
    });
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    // Cute Fetcher: status 0 = jaringan/SSL/DNS gagal di sisi Android. Lempar pesan yang jelas
    // (dulu hasilnya data kosong sehingga semua server tampak "gagal tanpa sebab").
    if (parsed.status === 0) {
      throw new Error(`${serverName}: jaringan gagal (${parsed.error || "tidak ada respons"})`);
    }
    response = {
      status: parsed.status,
      headers: parsed.headers || {},
      data: parsed.data,
    };
  } else if (shareBridge?.httpRequest) {
    await new Promise((r) => setTimeout(r, 60));
    let fetchUrl = options.url;
    if (options.params) {
      const q = new URLSearchParams(options.params).toString();
      if (q) fetchUrl += (fetchUrl.includes("?") ? "&" : "?") + q;
    }
    const bridgeOpts = {
      url: fetchUrl,
      method,
      headers,
      responseType: options.responseType,
    };
    if (options.data !== undefined)
      bridgeOpts.data =
        typeof options.data === "object"
          ? JSON.stringify(options.data)
          : String(options.data);
    const raw = shareBridge.httpRequest(JSON.stringify(bridgeOpts));
    const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
    response = {
      status: parsed.status,
      headers: parsed.headers || {},
      data: parsed.data,
    };
  } else if (CapacitorHttp) {
    if (method === "POST") {
      response = await CapacitorHttp.post(httpConfig);
    } else if (method === "PUT") {
      response = await CapacitorHttp.put(httpConfig);
    } else if (method === "DELETE") {
      response = await CapacitorHttp.delete(httpConfig);
    } else {
      response = await CapacitorHttp.get(httpConfig);
    }
  } else if (invoke) {
    // Native Rust reqwest for Tauri Desktop (100% CORS-free)
    let fetchUrl = options.url;
    if (options.params) {
      const q = new URLSearchParams(options.params).toString();
      if (q) fetchUrl += (fetchUrl.includes("?") ? "&" : "?") + q;
    }

    let bodyString = undefined;
    if (options.data !== undefined) {
      if (
        typeof options.data === "object" &&
        !(options.data instanceof FormData) &&
        !(options.data instanceof URLSearchParams)
      ) {
        if (
          headers["Content-Type"]?.includes("application/x-www-form-urlencoded")
        ) {
          bodyString = new URLSearchParams(options.data).toString();
        } else {
          bodyString = JSON.stringify(options.data);
          if (!headers["Content-Type"])
            headers["Content-Type"] = "application/json";
        }
      } else {
        bodyString = String(options.data);
      }
    }

    response = await invoke("tauri_http_request", {
      url: fetchUrl,
      method: method,
      headers: headers,
      body: bodyString,
    });
  } else {
    // Standard browser fetch fallback
    let fetchUrl = options.url;
    if (options.params) {
      const q = new URLSearchParams(options.params).toString();
      if (q) fetchUrl += (fetchUrl.includes("?") ? "&" : "?") + q;
    }

    let body = undefined;
    if (options.data !== undefined) {
      if (
        typeof options.data === "object" &&
        !(options.data instanceof FormData) &&
        !(options.data instanceof URLSearchParams)
      ) {
        if (
          headers["Content-Type"]?.includes("application/x-www-form-urlencoded")
        ) {
          body = new URLSearchParams(options.data).toString();
        } else {
          body = JSON.stringify(options.data);
          if (!headers["Content-Type"])
            headers["Content-Type"] = "application/json";
        }
      } else {
        body = options.data;
      }
    }

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), getRequestTimeout());
    const res = await fetch(fetchUrl, {
      method,
      headers,
      body,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);

    const resData =
      options.responseType === "arraybuffer"
        ? await res.arrayBuffer()
        : await res.text();
    response = {
      status: res.status,
      headers: Object.fromEntries(res.headers.entries()),
      data: resData,
    };
  }

  if (options.rawResponse) {
    return response;
  }

  if (options.parseJson !== false) {
    return parseJsonResponse(response.data, serverName);
  }

  return response.data;
}

/**
 * Helper to construct standardized scraper response objects.
 * @param {boolean} success - Whether the scrape operation succeeded
 * @param {object|string} payload - Data payload if success, error message if failure
 * @returns {{status: boolean, result?: object, message?: string}} Standardized response
 */
export function createScraperResult(success, payload, statusCode = null) {
  if (success) {
    return { status: true, result: payload };
  }
  const res = {
    status: false,
    message:
      typeof payload === "string"
        ? payload
        : payload?.message || "Scraping failed.",
  };
  if (statusCode !== null && statusCode !== undefined) {
    res.statusCode = statusCode;
  }
  return res;
}
