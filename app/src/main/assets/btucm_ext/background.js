"use strict";

const BTU_ORIGIN_URL = "https://classroom.btu.edu.ge/";
const BTU_HOST = "classroom.btu.edu.ge";
const SESSION_COOKIE_NAME = "argus_session";
const NATIVE_APP = "btucm_native";

let callbackObserved = false;
let authenticatedPageReached = false;
let lastAnonSent = false;
let lastReadyDelivered = false;
let nativePort = null;

function isBtuCookie(cookie) {
  if (!cookie || cookie.name !== SESSION_COOKIE_NAME) {
    return false;
  }
  const domain = (cookie.domain || "").toLowerCase();
  return domain === BTU_HOST || domain === "." + BTU_HOST;
}

async function sendToNative(payload) {
  try {
    await browser.runtime.sendNativeMessage(NATIVE_APP, payload);
  } catch (err) {
    // Avoid logging any payload that could contain session material
    console.warn("BTUCM native message dispatch failed");
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
  } catch (err) {
    console.warn("BTUCM cookie query failed");
  }
  return null;
}

async function reportInitialSessionIfPresent() {
  if (authenticatedPageReached) {
    return;
  }
  const cookie = await readBtuSessionCookie();
  if (cookie && cookie.value && !lastAnonSent) {
    lastAnonSent = true;
    await sendToNative({
      type: "SESSION_DETECTED",
      authenticated: false,
      session: cookie.value,
    });
  }
}

async function reportAuthenticatedSession() {
  const cookie = await readBtuSessionCookie();
  if (cookie && cookie.value) {
    lastReadyDelivered = true;
    await sendToNative({
      type: "SESSION_READY",
      session: cookie.value,
    });
  } else {
    await sendToNative({
      type: "SESSION_COOKIE_MISSING",
    });
  }
}

function connectControlPort() {
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
    nativePort.onMessage.addListener(async (msg) => {
      if (!msg || typeof msg !== "object") {
        return;
      }
      if (msg.action === "RESET_STATE") {
        callbackObserved = false;
        authenticatedPageReached = false;
        lastAnonSent = false;
        lastReadyDelivered = false;
      } else if (msg.action === "QUERY_BTU_SESSION") {
        if (msg.expectAuthenticated) {
          authenticatedPageReached = true;
          await reportAuthenticatedSession();
        } else {
          await reportInitialSessionIfPresent();
        }
      }
    });
    nativePort.onDisconnect.addListener(() => {
      nativePort = null;
    });
  } catch (err) {
    nativePort = null;
  }
}

browser.cookies.onChanged.addListener(async (changeInfo) => {
  if (changeInfo.removed || !isBtuCookie(changeInfo.cookie)) {
    return;
  }
  const cookie = changeInfo.cookie;
  if (!cookie.value) {
    return;
  }

  if (authenticatedPageReached) {
    lastReadyDelivered = true;
    await sendToNative({
      type: "SESSION_READY",
      session: cookie.value,
    });
  } else if (!lastAnonSent) {
    lastAnonSent = true;
    await sendToNative({
      type: "SESSION_DETECTED",
      authenticated: false,
      session: cookie.value,
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

connectControlPort();
