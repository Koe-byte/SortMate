// Supabase Edge Function: database webhook -> FCM HTTP v1.
// Deploy with:
//   npx supabase functions deploy notify-household --remote --use-api
// Required secret:
//   FCM_SERVICE_ACCOUNT_JSON  (Firebase service account key, never committed)
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are injected by the platform.

type WebhookPayload = {
  table: "bills" | "chores" | "members";
  record: Record<string, unknown> | null;
  old_record: Record<string, unknown> | null;
};

type Member = {
  id: string;
  auth_user_id: string;
  name: string;
  fcm_token: string | null;
};

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
};

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";

/**
 * Projects created with the new API keys expose SUPABASE_SECRET_KEYS as a JSON
 * map. Fall back to the legacy service_role JWT for older projects.
 */
function resolveServiceKey(): string {
  const raw = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as unknown;
      if (typeof parsed === "string") return parsed;
      if (Array.isArray(parsed)) {
        const first = parsed.find((value) => typeof value === "string");
        if (typeof first === "string") return first;
      }
      if (parsed && typeof parsed === "object") {
        const first = Object.values(parsed).find((value) => typeof value === "string");
        if (typeof first === "string") return first;
      }
    } catch {
      // Fall through to the legacy variable.
    }
  }
  return Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
}

const SERVICE_ROLE_KEY = resolveServiceKey();

let cachedToken: { value: string; expiresAt: number } | null = null;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

async function rest(path: string, init: RequestInit = {}): Promise<Response> {
  // Legacy service_role keys are JWTs and need the Authorization header.
  // New sb_secret_ keys are opaque and must only travel in the apikey header.
  const auth: Record<string, string> = { apikey: SERVICE_ROLE_KEY };
  if (SERVICE_ROLE_KEY.startsWith("eyJ")) {
    auth.Authorization = `Bearer ${SERVICE_ROLE_KEY}`;
  }
  return await fetch(`${SUPABASE_URL}/rest/v1/${path}`, {
    ...init,
    headers: {
      ...auth,
      "Content-Type": "application/json",
      ...(init.headers ?? {}),
    },
  });
}

async function webhookSecret(): Promise<string> {
  const response = await rest("app_config?key=eq.webhook_secret&select=value");
  if (!response.ok) {
    throw new Error(`Could not read webhook secret (${response.status}).`);
  }
  const rows = (await response.json()) as Array<{ value: string }>;
  return rows[0]?.value ?? "";
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const base64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

async function accessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) {
    return cachedToken.value;
  }

  const encoder = new TextEncoder();
  const header = base64Url(
    encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })),
  );
  const claims = base64Url(
    encoder.encode(
      JSON.stringify({
        iss: account.client_email,
        scope: "https://www.googleapis.com/auth/firebase.messaging",
        aud: "https://oauth2.googleapis.com/token",
        iat: now,
        exp: now + 3600,
      }),
    ),
  );
  const unsigned = `${header}.${claims}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      key,
      encoder.encode(unsigned),
    ),
  );
  const assertion = `${unsigned}.${base64Url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    throw new Error(`Google OAuth failed (${response.status}).`);
  }
  const payload = (await response.json()) as {
    access_token: string;
    expires_in: number;
  };
  cachedToken = {
    value: payload.access_token,
    expiresAt: now + (payload.expires_in ?? 3600),
  };
  return cachedToken.value;
}

function formatPeso(cents: number): string {
  return `₱${(cents / 100).toLocaleString("en-PH", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

function statusLabel(status: string): string {
  if (status === "HOME") return "At home";
  if (status === "CAMPUS") return "On campus";
  return "Away";
}

function buildMessage(
  payload: WebhookPayload,
): { type: string; title: string; body: string; skipMemberId?: string } | null {
  const record = payload.record;
  if (!record) return null;

  if (payload.table === "bills") {
    const title = String(record.title ?? "Bill");
    const total = Number(record.total_cents ?? 0);
    const settled = record.is_settled === true;
    const deleted = record.is_deleted === true;
    const oldSettled = payload.old_record?.is_settled === true;
    if (deleted) {
      return { type: "bill", title: "Bill removed", body: title };
    }
    if (settled && !oldSettled) {
      return {
        type: "bill",
        title: "Bill settled",
        body: `${title} · ${formatPeso(total)}`,
      };
    }
    return {
      type: "bill",
      title: payload.old_record ? "Bill updated" : "New bill",
      body: `${title} · ${formatPeso(total)}`,
    };
  }

  if (payload.table === "chores") {
    const title = String(record.title ?? "Chore");
    const completed = record.is_completed === true;
    const deleted = record.is_deleted === true;
    if (deleted) {
      return { type: "chore", title: "Chore removed", body: title };
    }
    return {
      type: "chore",
      title: completed ? "Chore completed" : "Chore update",
      body: title,
    };
  }

  const status = String(record.status ?? "");
  const oldStatus = payload.old_record?.status;
  if (payload.old_record && status === oldStatus) return null;
  const name = String(record.name ?? "A roommate");
  return {
    type: "presence",
    title: `${name} is ${statusLabel(status)}`,
    body: "Open SortMate to see the household.",
    skipMemberId: String(record.id ?? ""),
  };
}

async function sendPush(
  account: ServiceAccount,
  token: string,
  message: { type: string; title: string; body: string },
): Promise<boolean> {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${await accessToken(account)}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          notification: { title: message.title, body: message.body },
          data: { type: message.type },
          android: {
            priority: "high",
            notification: { channel_id: "household_alerts" },
          },
        },
      }),
    },
  );

  if (response.ok) return true;

  const detail = await response.text();
  if (detail.includes('"UNREGISTERED"')) {
    await rest(`members?fcm_token=eq.${encodeURIComponent(token)}`, {
      method: "PATCH",
      body: JSON.stringify({ fcm_token: null }),
    });
  }
  console.error(`FCM send failed (${response.status})`);
  return false;
}

Deno.serve(async (request) => {
  try {
    if (request.method !== "POST") {
      return json(405, { error: "Use POST." });
    }
    if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
      return json(500, { error: "Supabase environment is incomplete." });
    }

    const provided = request.headers.get("x-webhook-secret") ?? "";
    if (!provided || provided !== await webhookSecret()) {
      return json(401, { error: "Invalid webhook secret." });
    }

    const payload = (await request.json()) as WebhookPayload;
    if (!payload?.table || !payload.record) {
      return json(400, { error: "Missing table or record." });
    }

    const message = buildMessage(payload);
    if (!message) {
      return json(200, { sent: 0, skipped: "no-change" });
    }

    const householdId = String(payload.record.household_id ?? "");
    if (!householdId) {
      return json(400, { error: "Record has no household_id." });
    }

    const membersResponse = await rest(
      `members?household_id=eq.${encodeURIComponent(householdId)}&fcm_token=not.is.null&select=id,auth_user_id,name,fcm_token`,
    );
    if (!membersResponse.ok) {
      return json(502, { error: "Could not load household members." });
    }
    const members = (await membersResponse.json()) as Member[];

    const actor = String(
      payload.record.updated_by ?? payload.record.created_by ?? "",
    );
    const targets = members.filter((member) => {
      if (!member.fcm_token) return false;
      if (message.skipMemberId && member.id === message.skipMemberId) return false;
      if (actor && member.auth_user_id === actor) return false;
      return true;
    });

    if (targets.length === 0) {
      return json(200, { sent: 0, skipped: "no-targets" });
    }

    const rawAccount = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
    if (!rawAccount) {
      return json(500, { error: "FCM_SERVICE_ACCOUNT_JSON is not set." });
    }
    const account = JSON.parse(rawAccount) as ServiceAccount;

    let sent = 0;
    for (const member of targets) {
      if (await sendPush(account, member.fcm_token as string, message)) sent += 1;
    }

    return json(200, { sent, skipped: targets.length - sent });
  } catch (error) {
    console.error("notify-household failed", error instanceof Error ? error.name : "unknown error");
    return json(500, { error: "Unable to process the notification." });
  }
});
