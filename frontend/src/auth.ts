import Keycloak from "keycloak-js";
import type { Identity } from "./model";
let client: Keycloak;
let starting: Promise<Identity | null> | undefined;
export function startSession(): Promise<Identity | null> {
  return (starting ??= (async () => {
    const response = await fetch("/api/auth/oidc", {
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
    if (!response.ok)
      throw new Error(
        "The sign-in service is unavailable. Check the banking services and retry.",
      );
    const metadata = await response.json();
    const issuer = new URL(metadata.issuer);
    const realmIndex = issuer.pathname.lastIndexOf("/realms/");
    if (realmIndex < 0) throw new Error("Unsupported sign-in configuration.");
    client = new Keycloak({
      url: issuer.origin + issuer.pathname.slice(0, realmIndex),
      realm: issuer.pathname.slice(realmIndex + 8),
      clientId: metadata.clientId,
    });
    // Top-level SSO check restores a session after reload without third-party iframe cookies.
    // Tokens remain in memory; an unauthenticated browser gets the sign-in screen.
    const authenticated = await client.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
      redirectUri: window.location.origin + "/callback",
    });
    if (!authenticated) return null;
    const identity = await fetch("/api/auth/me", {
      headers: { Authorization: `Bearer ${client.token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
    if (!identity.ok)
      throw new Error(
        identity.status === 403
          ? "This identity has no banking role. Contact your administrator."
          : "Your sign-in could not be verified. Please sign in again.",
      );
    history.replaceState(null, "", "/");
    return identity.json();
  })());
}
export function login() {
  return client.login({ redirectUri: window.location.origin + "/callback" });
}
export function logout() {
  return client.logout({ redirectUri: window.location.origin + "/" });
}
export async function bearer(): Promise<string> {
  if (!client?.authenticated)
    throw new Error("Your session has ended. Sign in again.");
  try {
    await client.updateToken(30);
  } catch {
    client.clearToken();
    throw new Error("Your session has expired. Sign in again.");
  }
  if (!client.token) throw new Error("Your session has ended. Sign in again.");
  return client.token;
}
export function displayName() {
  return String(client?.tokenParsed?.preferred_username || "Banking user");
}

export function hasSession() {
  return Boolean(client?.authenticated);
}
