# Minecraft session proof version 2

Minecraft session authentication uses a client-generated nonce so the backend cannot choose the value that the mod authorizes at Mojang. Normal sign-in, separate raid-profile sign-in, and treasury sign-in use this protocol. Minecraft access tokens go to Mojang through authlib, never in these requests.

## Wire contract

The HTTP challenge response retains `challenge_id`, `server_id`, and `expires_at`, and adds `proof_version: 2`. Despite its existing name, `server_id` is an untrusted server seed. The mod requires version 2 before joining. Completion sends `challenge_id`, `username`, and required `client_nonce`.

The treasury challenge contains `type: "treasury_auth_challenge"`, `proof_version: 2`, and `nonce`. The response contains `type: "treasury_auth_response"`, the same server `nonce`, and required `client_nonce`. Successful confirmation contains `type: "treasury_authenticated"` and both matching nonces. The mod rejects a confirmation from an earlier attempt, including one with the same server seed.

A client nonce is 32 cryptographically random bytes, encoded as canonical unpadded base64url (43 characters). The mod creates it after receiving the server seed and sends it only after the Mojang join succeeds. Every new join gets fresh randomness. Missing or malformed client nonces fail authentication without trying the old raw-seed proof.

## Derivation

For each field below, append its byte length as an unsigned four-byte big-endian integer, then its bytes. Hash the concatenation with SHA-256 and encode the first 20 digest bytes as 40 lowercase hexadecimal characters, preserving leading zeroes.

1. UTF-8 `sequoia/minecraft-session-proof/v2`.
2. UTF-8 purpose: `account` or `treasury`.
3. UTF-8 canonical HTTPS origin.
4. UTF-8 canonical lowercase hyphenated challenge UUID for account authentication, or empty bytes for treasury.
5. Server seed bytes: the 20 bytes decoded from HTTP `server_id`, or 24 bytes decoded from the treasury base64url `nonce`.
6. The 32 decoded client nonce bytes.

The client joins only this derived value. The backend recomputes it from its stored challenge, the submitted client nonce, and trusted deployment configuration before calling `hasJoined`. Existing challenge expiry, atomic consumption, verified account-link checks, and treasury UUID/name checks still apply.

The client obtains the origin from its build-selected API or WebSocket URL. The backend obtains it from `app.public-base-url` (`APP_PUBLIC_BASE_URL`). Origin normalization lowercases the hostname, removes port 443 and the path, preserves nondefault ports, and maps `wss` to `https`. Userinfo, queries, fragments, and insecure schemes are rejected. Request headers and response fields cannot override this origin. Main and raid profiles share the account purpose; distinct origins separate deployments.

## Client and backend configuration

Each backend's `APP_PUBLIC_BASE_URL` must match its client's URL origin, including a separate raid-profile backend.
The mod requires version 2 challenges, and the backend requires `client_nonce`.
Deploy the matching backend before distributing the mod, and verify account and treasury sign-in
against it. If the backend must roll back to an incompatible protocol, roll back the mod with it.

The backend's Minecraft-auth and treasury minimum-version settings control update messages.
Unexpired application tokens retain their existing lifetime.

## Verify the protocol

`MinecraftSessionProofTest` in each repository includes identical independent vectors for production, staging, a nondefault port, and treasury. The client tests also exercise an attacker-chosen game hash, fresh randomness, failed joins, legacy-version rejection, and reset/confirmation correlation. Backend tests retain account-link, expiry, replay, and treasury identity checks with derived proofs.
