# Verify the /apps Nav and Avatar Dropdown

This guide shows you how to verify, in a real browser, the authenticated navigation
and avatar dropdown that appear after signing in to the Bottin client. It covers the
interactive behaviour that server-side tests cannot assert: the client-side reveal of
the nav, the dropdown toggle, and the real navigation performed by Logout.

The server-rendered guards for this feature (the nav section renders, the avatar
points at an existing asset, Logout is wired to a real navigation) live in
`AppsControllerTest` and run with the normal build. Use this guide for the end-to-end
flow on top of a running client.

## Prerequisites

- The `bottin-client-ui` module running locally.
- A Chromium-based browser.

## Start the Client

Run the client with page and cookie settings suitable for local, non-HTTPS use:

```bash
BOTTIN_CLIENT_PORT=8090 \
COOKIE_SECURE=false \
BOTTIN_EXTERNAL_URL=http://localhost:8090 \
THYMELEAF_CACHE=false \
mvn -pl bottin-client-ui spring-boot:run
```

The client is ready when `http://localhost:8090/apps` responds.

## Verify the Flow

1. **Sign in.** Open `http://localhost:8090/login`, paste a valid `nsec`, and submit.
   You are redirected to `/apps`.
2. **Authenticated nav is revealed.** On `/apps`, the nav shows a **Search** link and
   the **avatar** button. These are hidden until the client confirms an active session,
   so their appearance verifies the client-side reveal.
3. **Avatar renders.** The avatar shows the profile picture when the stored identity
   carries one, and the bundled default (`/img/default-avatar.svg`) otherwise — never a
   broken-image icon.
4. **Dropdown toggles.** Click the avatar. The menu opens with **Profile**, **Settings**,
   and **Logout**. Click the avatar again, or click outside the menu, to close it.
5. **Profile navigates.** Click **Profile**; the browser loads `/profile`.
6. **Logout returns to /login.** Open the dropdown, click **Logout**, and accept the
   confirmation. The browser navigates to a fully rendered `/login` page — not a blank
   page — and the authenticated nav is no longer shown.

## Confirm the Session Was Cleared

After logout, the session endpoint returns `401`:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8090/api/v1/auth/session
```

A `401` confirms the NAP session cookie was cleared server-side. The stored identity is
retained on purpose: the private key is held encrypted, so a returning user can unlock
with their passphrase on `/login`.
