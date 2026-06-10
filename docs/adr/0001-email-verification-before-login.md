# Email verification is mandatory before login

Verification Codes were already generated and validated in-app, but never delivered — they were printed to stdout. We decided to send them via SMTP (Gmail) using Spring's `JavaMailSender` with HTML templates (Thymeleaf). Email is now mandatory at signup, and all login attempts (by username or email) are gated behind `emailVerified == true`. Signup no longer returns a JWT; instead it returns a `SignupResponse` with a masked email address, and the JWT is only issued after successful verification via `/verify-email`.

## Considered Options

- **Synchronous email sending** — simpler, but couples request latency to an external SMTP server. Rejected because the Verification Code is persisted regardless, and users can resend.
- **Plain text emails** — zero dependencies, but chosen HTML (Thymeleaf) for a professional look on a transactional email that users interact with on every signup.
- **Force existing users to verify** — would lock out anyone who signed up without an email. Rejected in favor of grandfathering existing users via a Flyway migration (`emailVerified = true` where `email IS NULL`).
- **Verification-only EmailService** — narrower interface, but rejected in favor of a generic `EmailService(to, subject, templateName, templateVars)` to support future email types (password reset, notifications) without refactoring the interface.

## Consequences

- The signup API contract changes: `POST /api/v1/auth/signup` returns `201 SignupResponse` instead of `200 LoginResponse`. Frontend must be updated to show a "check your email" screen.
- New environment variables required: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`.
- `spring-boot-starter-mail` and `spring-boot-starter-thymeleaf` are added as dependencies.
