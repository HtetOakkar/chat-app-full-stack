# Production Security Baseline

This checklist captures the security baseline implemented from the Security Hardening PRD and issues #39 through #46.

## Required Environment

- `DB_URL`: Production database JDBC URL. Do not use local development credentials in production.
- `DB_USER`: Least-privilege database user.
- `DB_PASSWORD`: Secret-managed database password.
- `JWT_SECRET`: Long random signing secret, managed outside source control.
- `JWT_EXPIRATION_MS`: Access token lifetime. Prefer short lifetimes for browser clients.
- `REDIS_HOST`: Redis host for presence, sessions, and real-time support.
- `REDIS_PORT`: Redis port.
- `APP_CORS_ALLOWED_ORIGINS`: Comma-separated list of trusted frontend origins. The current testing default is `*`; use exact origins only before production, for example `https://chat.example.com`.
- `APP_CORS_ALLOW_LOCAL_DEVELOPMENT_ORIGINS`: `false` in production. Set `true` only for deliberate local development.
- `APP_CORS_ALLOW_ANY_ORIGIN`: Temporarily defaults to `true` for LAN testing. Set `false` before production.
- `NEXT_PUBLIC_API_PORT`: Frontend LAN testing backend port. Defaults to `8181`.
- `NEXT_PUBLIC_API_URL`: Optional explicit frontend API base URL. Use this if the browser should call a backend host that differs from the frontend host.
- `AUTH_RATE_LIMIT_LOGIN_MAX_ATTEMPTS`: Login attempts per source/account window.
- `AUTH_RATE_LIMIT_SIGNUP_MAX_ATTEMPTS`: Signup attempts per source window.
- `AUTH_RATE_LIMIT_VERIFY_EMAIL_MAX_ATTEMPTS`: Public email verification attempts per source/account window.
- `AUTH_RATE_LIMIT_RESEND_CODE_MAX_ATTEMPTS`: Public resend attempts per source/account window.
- `AUTH_RATE_LIMIT_WINDOW_SECONDS`: Rate-limit window in seconds.
- `RESEND_API_KEY`: Secret-managed email provider API key.
- `RESEND_TEMPLATE_VERIFICATION_ID`: Verification email template ID. If omitted, the app attempts to create one.
- `FLYWAY_BASELINE_ON_MIGRATE`: Flyway baseline setting.
- `FLYWAY_BASELINE_VERSION`: Flyway baseline version.

## Origin Policy

REST CORS and STOMP WebSocket endpoints share the same trusted-origin policy.

- Wildcard origins are temporarily allowed for testing.
- Production config must replace wildcard origins with explicit trusted origins.
- Localhost patterns are opt-in only through `APP_CORS_ALLOW_LOCAL_DEVELOPMENT_ORIGINS=true`.
- Do not include private-network wildcard patterns in production.

## Browser Auth

ADR-0004 records the selected browser auth transport: bearer access tokens are kept in memory only.

- Do not write bearer tokens to `localStorage`, `sessionStorage`, or cookies readable by JavaScript.
- REST requests continue to use `Authorization: Bearer <token>`.
- STOMP connections continue to send the bearer token in the connect headers.
- Logout clears the in-memory token and authenticated UI state.
- A page reload requires a new login unless a future HttpOnly refresh-token flow is added.

## Profile And Call Authorization

- Owner profile views can include private profile fields.
- Other-user profile views omit email, birth date, and email verification state.
- Call offers require accepted/contact relationships and respect block state.
- Call answer, ICE, cancel, reject, and hangup frames are forwarded only between active call participants.

## Logging And Errors

- Verification code values must never be logged or printed to stdout/stderr.
- Verification logs may include safe metadata such as user ID, username, and delivery action.
- Unexpected server errors return a generic message to clients.
- Exception details remain in server logs for operators.
- Expected validation and domain errors may return safe user-facing messages.

## Abuse Controls

Public auth flows are protected by in-memory fixed-window rate limits:

- Login is limited by source IP and account identifier.
- Signup is limited by source IP.
- Public email verification is limited by source IP and account identifier.
- Public resend-code is limited by source IP and account identifier.

For multi-instance production deployments, replace or back this limiter with a shared store such as Redis so limits apply across all backend instances.

## System Account

The `system` account exists only as a service principal for system-generated messages and call records.

- It is created with a random non-recoverable password.
- It is not login-capable through the public auth endpoint.
- It is not assigned an administrator role for login purposes.
- Source access no longer reveals a working system-account credential.

## Related Work

- #39 Lock Down Public and Private Profile Access
- #40 Restrict Production CORS and WebSocket Origins
- #41 Authorize Every Call Signaling Action by Active Participants
- #42 Stop Leaking Verification Codes and Production Exception Details
- #43 Add Abuse Controls to Login and Verification Code Flows
- #44 Replace the Hardcoded System Account Password
- #45 Choose and Implement Safer Browser Auth Transport
- #46 Document the Production Security Baseline
