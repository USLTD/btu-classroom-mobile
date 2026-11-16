"use strict";

const BTU_ORIGIN_URL = "https://classroom.btu.edu.ge/";
const BTU_HOST = "classroom.btu.edu.ge";
const SESSION_COOKIE_NAME = "argus_session";
const NATIVE_APP = "btucm_native";

let callbackObserved = false;
let authenticatedPageReached = false;
let lastAnonSent = false;
let lastReadyDelivered = false;
let lastKnownExpirationDate = null;
let expirationReported = false;
let activeUserAgentMode = "DEFAULT";
let activeUserAgentValue = null;
let nativePort = null;

function isBtuDomain(domain) {
  if (!domain) return false;
  const clean = domain.toLowerCase().replace(/^\./, "");
  return clean === BTU_HOST || clean.endsWith(".btu.edu.ge");
}

function isDisallowedCookieDomain(domain) {
  if (!domain) return true;
  const clean = domain.toLowerCase().replace(/^\./, "");
  return clean === "google.com" || clean.endsWith(".google.com") || clean.endsWith(".youtube.com");
}

function isBtuCookie(cookie) {
  if (!cookie || cookie.name !== SESSION_COOKIE_NAME) {
    return false;
  }
  return isBtuDomain(cookie.domain);
}

function isCookieExpired(cookie) {
  if (!cookie || typeof cookie.expirationDate !== "number") {
    return false;
  }
  return cookie.expirationDate <= Math.floor(Date.now() / 1000);
}

function sanitizeCookieMetadata(cookie) {
  const nowSec = Math.floor(Date.now() / 1000);
  const exp = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
  return {
    name: cookie.name || "",
    domain: cookie.domain || "",
    path: cookie.path || "/",
    secure: Boolean(cookie.secure),
    httpOnly: Boolean(cookie.httpOnly),
    sameSite: cookie.sameSite || "unspecified",
    isSessionCookie: Boolean(cookie.session || exp === null),
    expirationDate: exp,
    isExpired: exp !== null && exp <= nowSec,
  };
}

async function sendToNative(payload) {
  try {
    await browser.runtime.sendNativeMessage(NATIVE_APP, payload);
  } catch (_) {
    if (nativePort) {
      try {
        nativePort.postMessage(payload);
      } catch (_) {
        // Ignore when native listener is tearing down
      }
    }
  }
}

async function readBtuSessionCookie() {
  try {
    const direct = await browser.cookies.get({
      url: BTU_ORIGIN_URL,
      name: SESSION_COOKIE_NAME,
    });
    if (direct && direct.value) {
      return direct;
    }

    const stores = await browser.cookies.getAllCookieStores();
    for (const store of stores) {
      const candidate = await browser.cookies.get({
        url: BTU_ORIGIN_URL,
        name: SESSION_COOKIE_NAME,
        storeId: store.id,
      });
      if (candidate && candidate.value) {
        return candidate;
      }
    }
  } catch (_) {
    // Ignore store errors
  }
  return null;
}

async function listCookiesForDomain(requestedDomain) {
  const targetDomain = (requestedDomain || BTU_HOST).trim().toLowerCase();
  if (isDisallowedCookieDomain(targetDomain)) {
    await sendToNative({
      type: "DOMAIN_COOKIES_LIST",
      domain: targetDomain,
      rejected: true,
      reason: "Reading Google cookies is strictly prohibited.",
      cookies: [],
    });
    return;
  }

  try {
    const allCookies = [];
    const seenKeys = new Set();
    const stores = await browser.cookies.getAllCookieStores();
    const storeIds = stores.length > 0 ? stores.map((s) => s.id) : [undefined];

    for (const storeId of storeIds) {
      const query = storeId ? { domain: targetDomain, storeId } : { domain: targetDomain };
      const found = await browser.cookies.getAll(query);
      for (const c of found) {
        const key = `${c.storeId || "default"}:${c.domain}:${c.path}:${c.name}`;
        if (!seenKeys.has(key)) {
          seenKeys.add(key);
          allCookies.push(sanitizeCookieMetadata(c));
        }
      }
    }

    await sendToNative({
      type: "DOMAIN_COOKIES_LIST",
      domain: targetDomain,
      rejected: false,
      cookies: allCookies,
    });
  } catch (err) {
    await sendToNative({
      type: "DOMAIN_COOKIES_LIST",
      domain: targetDomain,
      rejected: false,
      error: String(err && err.message ? err.message : "Failed to list cookies"),
      cookies: [],
    });
  }
}

async function deleteCookiesForDomain(requestedDomain) {
  const targetDomain = (requestedDomain || BTU_HOST).trim().toLowerCase();
  if (isDisallowedCookieDomain(targetDomain)) {
    await sendToNative({
      type: "DOMAIN_COOKIES_DELETED",
      domain: targetDomain,
      rejected: true,
      deletedCount: 0,
      reason: "Deleting Google cookies is strictly prohibited.",
    });
    return;
  }

  let deletedCount = 0;
  const deletedNames = [];

  try {
    const stores = await browser.cookies.getAllCookieStores();
    const storeIds = stores.length > 0 ? stores.map((s) => s.id) : [undefined];

    for (const storeId of storeIds) {
      const query = storeId ? { domain: targetDomain, storeId } : { domain: targetDomain };
      const found = await browser.cookies.getAll(query);
      for (const cookie of found) {
        const protocol = cookie.secure ? "https://" : "http://";
        const cleanHost = (cookie.domain || targetDomain).replace(/^\./, "");
        const cookieUrl = protocol + cleanHost + (cookie.path || "/");
        const removeParams = {
          url: cookieUrl,
          name: cookie.name,
        };
        if (cookie.storeId) {
          removeParams.storeId = cookie.storeId;
        }
        const removed = await browser.cookies.remove(removeParams);
        if (removed) {
          deletedCount += 1;
          deletedNames.push(cookie.name);
        }
      }
    }

    if (isBtuDomain(targetDomain)) {
      callbackObserved = false;
      authenticatedPageReached = false;
      lastAnonSent = false;
      lastReadyDelivered = false;
      lastKnownExpirationDate = null;
      expirationReported = false;
    }

    await sendToNative({
      type: "DOMAIN_COOKIES_DELETED",
      domain: targetDomain,
      rejected: false,
      deletedCount,
      deletedNames,
    });
  } catch (err) {
    await sendToNative({
      type: "DOMAIN_COOKIES_DELETED",
      domain: targetDomain,
      rejected: false,
      deletedCount,
      error: String(err && err.message ? err.message : "Failed to delete cookies"),
    });
  }
}

async function logBtuCookiesToDiagnostic() {
  try {
    const found = await browser.cookies.getAll({ domain: BTU_HOST });
    const sanitized = found.map(sanitizeCookieMetadata);
    await sendToNative({
      type: "BTU_COOKIES_DEBUG_LOG",
      domain: BTU_HOST,
      cookies: sanitized,
    });
  } catch (err) {
    await sendToNative({
      type: "BTU_COOKIES_DEBUG_LOG",
      domain: BTU_HOST,
      error: String(err && err.message ? err.message : "Cookie debug inspection failed"),
      cookies: [],
    });
  }
}

async function checkBtuSessionExpiration() {
  const cookie = await readBtuSessionCookie();
  if (!cookie) {
    if ((lastAnonSent || lastReadyDelivered) && !expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "cookie_missing_or_expired",
      });
    }
    return;
  }

  if (isCookieExpired(cookie)) {
    if (!expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "expiration_date_passed",
        expirationDate: cookie.expirationDate,
      });
    }
  } else {
    lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
    expirationReported = false;
  }
}

async function reportInitialSessionIfPresent() {
  if (authenticatedPageReached) {
    return;
  }
  const cookie = await readBtuSessionCookie();
  if (cookie && cookie.value) {
    if (isCookieExpired(cookie)) {
      await checkBtuSessionExpiration();
      return;
    }
    lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
    expirationReported = false;
    if (!lastAnonSent) {
      lastAnonSent = true;
      await sendToNative({
        type: "SESSION_DETECTED",
        authenticated: false,
        session: cookie.value,
        expirationDate: lastKnownExpirationDate,
      });
    }
  }
}

async function reportAuthenticatedSession() {
  const cookie = await readBtuSessionCookie();
  if (cookie && cookie.value) {
    if (isCookieExpired(cookie)) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "expiration_date_passed",
        expirationDate: cookie.expirationDate,
      });
      return;
    }
    lastReadyDelivered = true;
    expirationReported = false;
    lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;
    await sendToNative({
      type: "SESSION_READY",
      session: cookie.value,
      expirationDate: lastKnownExpirationDate,
    });
  } else {
    await sendToNative({
      type: "SESSION_COOKIE_MISSING",
    });
  }
}

function setUserAgentOverride(modeId, userAgent) {
  activeUserAgentMode = modeId || "DEFAULT";
  activeUserAgentValue = userAgent && String(userAgent).trim().length > 0 ? String(userAgent).trim() : null;
  sendToNative({
    type: "USER_AGENT_UPDATED",
    modeId: activeUserAgentMode,
    hasOverride: Boolean(activeUserAgentValue),
  });
}

// Dynamic User-Agent header modifier for BTU Classroom & Google OAuth testing
browser.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    if (!activeUserAgentValue || !details || !Array.isArray(details.requestHeaders)) {
      return {};
    }
    let replaced = false;
    const updatedHeaders = details.requestHeaders.map((header) => {
      if (header.name && header.name.toLowerCase() === "user-agent") {
        replaced = true;
        return { name: header.name, value: activeUserAgentValue };
      }
      return header;
    });
    if (!replaced) {
      updatedHeaders.push({ name: "User-Agent", value: activeUserAgentValue });
    }
    return { requestHeaders: updatedHeaders };
  },
  {
    urls: [
      "https://classroom.btu.edu.ge/*",
      "https://accounts.google.com/*",
    ],
  },
  ["blocking", "requestHeaders"]
);

function connectControlPort() {
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
    nativePort.onMessage.addListener(async (msg) => {
      if (!msg || typeof msg !== "object") {
        return;
      }
      switch (msg.action) {
        case "RESET_STATE":
          callbackObserved = false;
          authenticatedPageReached = false;
          lastAnonSent = false;
          lastReadyDelivered = false;
          lastKnownExpirationDate = null;
          expirationReported = false;
          break;
        case "QUERY_BTU_SESSION":
          if (msg.expectAuthenticated) {
            authenticatedPageReached = true;
            await reportAuthenticatedSession();
          } else {
            await reportInitialSessionIfPresent();
          }
          break;
        case "SET_USER_AGENT":
          setUserAgentOverride(msg.modeId, msg.userAgent);
          break;
        case "LIST_DOMAIN_COOKIES":
          await listCookiesForDomain(msg.domain);
          break;
        case "DELETE_DOMAIN_COOKIES":
          await deleteCookiesForDomain(msg.domain);
          break;
        case "LOG_BTU_COOKIES":
          await logBtuCookiesToDiagnostic();
          break;
        case "CHECK_SESSION_EXPIRATION":
          await checkBtuSessionExpiration();
          break;
        default:
          break;
      }
    });
    nativePort.onDisconnect.addListener(() => {
      nativePort = null;
    });
  } catch (_) {
    nativePort = null;
  }
}

browser.cookies.onChanged.addListener(async (changeInfo) => {
  if (!isBtuCookie(changeInfo.cookie)) {
    return;
  }
  const cookie = changeInfo.cookie;

  if (changeInfo.removed) {
    const cause = changeInfo.cause || "removed";
    if ((lastAnonSent || lastReadyDelivered) && !expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: cause,
      });
    }
    return;
  }

  if (!cookie.value) {
    return;
  }

  if (isCookieExpired(cookie)) {
    if (!expirationReported) {
      expirationReported = true;
      await sendToNative({
        type: "SESSION_EXPIRED",
        reason: "expiration_date_passed",
        expirationDate: cookie.expirationDate,
      });
    }
    return;
  }

  expirationReported = false;
  lastKnownExpirationDate = typeof cookie.expirationDate === "number" ? cookie.expirationDate : null;

  if (authenticatedPageReached) {
    lastReadyDelivered = true;
    await sendToNative({
      type: "SESSION_READY",
      session: cookie.value,
      expirationDate: lastKnownExpirationDate,
    });
  } else if (!lastAnonSent) {
    lastAnonSent = true;
    await sendToNative({
      type: "SESSION_DETECTED",
      authenticated: false,
      session: cookie.value,
      expirationDate: lastKnownExpirationDate,
    });
  }
});

async function handleBtuNavigation(details) {
  if (details.frameId !== 0 || !details.url) {
    return;
  }
  let parsedUrl;
  try {
    parsedUrl = new URL(details.url);
  } catch (_) {
    return;
  }

  if (parsedUrl.hostname.toLowerCase() !== BTU_HOST) {
    return;
  }

  const pathname = parsedUrl.pathname || "/";

  if (pathname.startsWith("/ge/login/oauth")) {
    if (!callbackObserved) {
      callbackObserved = true;
      await sendToNative({ type: "BTU_CALLBACK_OBSERVED" });
    }
    const hasCode = parsedUrl.searchParams.has("code");
    const hasAccessToken = parsedUrl.searchParams.has("access_token") || (parsedUrl.hash && parsedUrl.hash.includes("access_token"));
    const hasError = parsedUrl.searchParams.has("error");
    if (hasCode || hasAccessToken || hasError) {
      await sendToNative({
        type: "OAUTH_TOKEN_CAPTURED",
        hasAuthorizationCode: hasCode,
        hasAccessToken: hasAccessToken,
        oauthError: hasError ? parsedUrl.searchParams.get("error") : null,
        tokenPayload: parsedUrl.searchParams.get("code") || null,
      });
    }
    await reportInitialSessionIfPresent();
    return;
  }

  if (pathname.startsWith("/ge/student/")) {
    authenticatedPageReached = true;
    if (!lastReadyDelivered) {
      await sendToNative({ type: "AUTHENTICATED_PAGE_REACHED" });
      await reportAuthenticatedSession();
    }
    return;
  }

  if (pathname === "/" || pathname.startsWith("/ge/login/index")) {
    await reportInitialSessionIfPresent();
  }
}

const btuFilter = { url: [{ hostEquals: BTU_HOST }] };
browser.webNavigation.onCommitted.addListener(handleBtuNavigation, btuFilter);
browser.webNavigation.onCompleted.addListener(handleBtuNavigation, btuFilter);

// Periodic expiration watchdog for active argus_session cookies
setInterval(() => {
  if (lastKnownExpirationDate !== null || lastReadyDelivered || lastAnonSent) {
    checkBtuSessionExpiration();
  }
}, 15000);

connectControlPort();
