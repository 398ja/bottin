# UI Flow: Nostr Client Onboarding & Account Management

> Interactive HTML mockups for each page are available in the [`mockups/`](mockups/index.html) directory. Open `mockups/index.html` in a browser to view desktop and mobile layouts side by side.

## Page Inventory

### Authentication & Onboarding

| # | Route | Template | Controller | Auth | Description |
|---|-------|----------|------------|------|-------------|
| P1 | `/` | — | `OnboardingController` | No | Root redirect: if no identity → `/onboarding`; if has identity but no session → `/login`; if authenticated → `/search` |
| P2 | `/onboarding` | `onboarding/step-method.html` | `OnboardingController` | No | Wizard step 1: choose "Create New Key" or "Import Key" (paste nsec) |
| P3 | (HTMX fragment) | `onboarding/step-profile.html` | `OnboardingController` | No | Wizard step 2: profile form — username, display name, about, picture (Blossom upload), banner (Blossom upload), NIP-05 auto-derived, lud16, website |
| P4 | (HTMX fragment) | `onboarding/step-security.html` | `OnboardingController` | No | Wizard step 3: set local encryption password (min 8 chars, strength meter, confirmation) |
| P5 | (HTMX fragment) | `onboarding/step-confirm.html` | `OnboardingController` | No | Wizard step 4: review card of all entered data before final submission |
| P6 | `/onboarding/complete` | — | `OnboardingController` | No | Processing action: generates key (or imports), encrypts with password, stores in localStorage |
| P7 | `/onboarding/welcome` | `onboarding/step-welcome.html` | `OnboardingController` | No | Success: nsec backup prompt (masked, copy, checkbox acknowledgment), welcome message, redirect to search after dismissal |
| P8 | `/login` | `login.html` | `LoginController` | No | Login: paste nsec → NAP challenge → sign locally → submit proof → session cookie |

### Backup & Restore

| # | Route | Template | Controller | Auth | Description |
|---|-------|----------|------------|------|-------------|
| P9 | `/restore` | `backup.html` | `BackupController` | No | Restore identity from encrypted backup file: file picker + passphrase → decrypt client-side → store in localStorage → redirect to login |

### Search & Profiles

| # | Route | Template | Controller | Auth | Description |
|---|-------|----------|------------|------|-------------|
| P10 | `/search` | `search.html` | `SearchController` | Yes | Profile search: query input + results list (avatar, name, nip05, follow/block actions) |
| P11 | `/profile` | `profile.html` | `ProfileController` | Yes | Own profile view/edit: display and edit kind-0 metadata, copy npub |
| P12 | `/profile/{pubkey}` | `profile.html` | `ProfileController` | Yes | Other user's profile view: read-only display, follow/block actions |

### Settings

| # | Route | Template | Controller | Auth | Description |
|---|-------|----------|------------|------|-------------|
| P13 | `/settings` | `settings/index.html` | `SettingsController` | Yes | Settings overview hub: links to relays, security, follows, blocks, backup, profile |
| P14 | `/settings/relays` | `settings/relays.html` | `SettingsController` | Yes | Relay management: read relay list, write relay list, add relay (URL + read/write toggles), remove, Save & Publish |
| P15 | `/settings/security` | `settings/security.html` | `SettingsController` | Yes | Security settings: change passphrase, reveal nsec (password-gated), logout |
| P16 | `/settings/follows` | `settings/follows.html` | `FollowController` | Yes | Follow list: list of followed pubkeys with avatar, name, unfollow button |
| P17 | `/settings/blocks` | `settings/blocks.html` | `BlockController` | Yes | Block list: list of blocked pubkeys with avatar, name, unblock button |
| P18 | `/settings/backup` | `backup.html` | `BackupController` | Yes | Export encrypted backup (download file) |

### API Endpoints (Non-Page)

| Method | Route | Controller | Auth | Purpose |
|--------|-------|------------|------|---------|
| POST | `/api/v1/auth/init` | nap-spring | No | Initiate NAP challenge |
| POST | `/api/v1/auth/complete` | nap-spring | No | Submit NIP-98 proof, receive session cookie |
| GET | `/api/v1/auth/session` | nap-spring | Cookie | Check/refresh current session |
| POST | `/api/v1/auth/logout` | nap-spring | Cookie | Revoke session |
| GET | `/api/v1/search?q=&limit=` | `SearchController` | Cookie (optional) | Search nostrdb profiles |
| GET | `/api/v1/resolve/{username}` | `OnboardingController` | No | Username availability check (debounced 500ms) |
| POST | `/api/v1/follow` | `FollowController` | Cookie | Follow a pubkey |
| POST | `/api/v1/unfollow` | `FollowController` | Cookie | Unfollow a pubkey |
| GET | `/api/v1/follows` | `FollowController` | Cookie | List followed pubkeys |
| POST | `/api/v1/block` | `BlockController` | Cookie | Block a pubkey |
| POST | `/api/v1/unblock` | `BlockController` | Cookie | Unblock a pubkey |
| GET | `/api/v1/blocks` | `BlockController` | Cookie | List blocked pubkeys |
| GET | `/api/v1/relays` | `RelayController` | Cookie | List configured relays |
| POST | `/api/v1/relays` | `RelayController` | Cookie | Add a relay |
| PUT | `/api/v1/relays` | `RelayController` | Cookie | Update relay permissions |
| DELETE | `/api/v1/relays` | `RelayController` | Cookie | Remove a relay |
| POST | `/api/v1/relays/publish` | `RelayController` | Cookie | Publish NIP-65 kind-10002 event |
| GET | `/api/v1/backup/export` | `BackupController` | Cookie | Download encrypted backup file |
| POST | `/api/v1/backup/restore` | `BackupController` | No | Upload and restore from backup file |

---

## Navigation Structure

```
                         ┌──────────────────────┐
                         │   App Shell (layout)  │
                         │  ┌──────────────────┐ │
                         │  │ Nav Bar           │ │
                         │  │ [Logo] [Search]   │ │
                         │  │ [Settings] [User] │ │
                         │  │ [Logout]          │ │
                         │  └──────────────────┘ │
                         │  ┌──────────────────┐ │
                         │  │ Page Content      │ │
                         │  │ (renders here)    │ │
                         │  └──────────────────┘ │
                         └──────────────────────┘
```

### Nav Bar States

**Unauthenticated** (no session cookie):
- Logo (links to `/`)
- No other links

**Authenticated** (valid session cookie):
- Logo (links to `/search`)
- Search icon (links to `/search`)
- Settings icon (links to `/settings`)
- Profile avatar (links to `/profile`)
- Logout button (POST `/api/v1/auth/logout`)

---

## User Flows

### Flow 1: New User Onboarding

```
[No identity stored]          [Identity exists but no session]
        │                                │
        ▼                                ▼
   ┌──────────┐                    ┌──────────┐
   │  GET /   │                    │  GET /   │
   │ redirect │                    │ redirect │
   └────┬─────┘                    └────┬─────┘
        ▼                              ▼
   ┌──────────┐                   ┌──────────┐
   │ /onboarding                   │  /login  │
   │ step-method                  │ paste    │
   │ [Create] [Import]            │ nsec     │
   └────┬─────┘                   │          │
        │ (choose method)         │ NAP init │
        ▼                         │ NAP comp │
   ┌──────────┐                   │ session  │
   │ step-    │                   │ cookie   │
   │ profile  │                   └────┬─────┘
   │ (HTMX)   │                        │
   └────┬─────┘                        ▼
        │ (submit profile)       ┌──────────┐
        ▼                        │  /search │
   ┌──────────┐                  └──────────┘
   │ step-    │
   │ security │
   │ password │
   └────┬─────┘
        │ (submit password)
        ▼
   ┌──────────┐
   │ step-    │
   │ confirm  │
   │ review   │
   └────┬─────┘
        │ (confirm → /onboarding/complete)
        ▼
   ┌──────────┐
   │ complete │
   │ generate │
   │ key      │
   │ encrypt  │
   │ store    │
   └────┬─────┘
        ▼
   ┌──────────┐
   │ welcome  │
   │ backup   │
   │ nsec     │
   │ dismissed│
   └────┬─────┘
        │
        ▼
   ┌──────────┐
   │  /search │
   └──────────┘
```

### Flow 2: Returning User Login

```
   ┌──────────┐
   │  GET /   │
   │ (has     │
   │ identity │
   │ but no   │
   │ session) │
   └────┬─────┘
        │ redirect
        ▼
   ┌──────────┐
   │  /login  │
   │          │
   │ Paste    │
   │ nsec     │──────────┐
   │          │          │ "Restore from backup"
   │ [Sign In]│          ▼
   └────┬─────┘    ┌──────────┐
        │          │ /restore │
        ▼          │ file     │
   ┌──────────┐    │ + pass   │
   │ NAP init │    │ decrypt  │
   │ POST     │    │ store    │
   │ /auth/   │    └────┬─────┘
   │ init     │         │
   └────┬─────┘         │
        │ challenge     │ redirect
        ▼               │
   ┌──────────┐         │
   │ Sign     │         │
   │ NIP-98   │         │
   │ event    │         │
   │ (client) │         │
   └────┬─────┘         │
        │ proof         │
        ▼               │
   ┌──────────┐         │
   │ NAP comp │         │
   │ POST     │         │
   │ /auth/   │         │
   │ complete │         │
   └────┬─────┘         │
        │ session       │
        │ cookie        │
        ▼               ▼
   ┌──────────┐    ┌──────────┐
   │  /search │    │  /login  │
   │          │    │ (then    │
   │          │    │ NAP flow)│
   └──────────┘    └──────────┘
```

### Flow 3: Authenticated Browsing

```
            ┌──────────────────────────────────────────────┐
            │                                              │
            ▼                                              │
   ┌──────────┐     search query      ┌──────────┐         │
   │  /search │──────────────────────►│ results  │         │
   │ (landing)│                       │ list     │         │
   └──────────┘                       └────┬─────┘         │
        ▲                                  │               │
        │                                  │ click profile │
        │                                  ▼               │
        │                           ┌──────────┐           │
        │                           │/profile/ │           │
        │                           │ {pubkey} │           │
        │                           │ view     │           │
        │                           │ [Follow] │           │
        │                           │ [Block]  │           │
        │                           └────┬─────┘           │
        │                                │                 │
        │                          follow/block action     │
        │                                │                 │
        │                           ┌────▼────┐           │
        │                           │ POST    │           │
        │                           │ /follow │           │
        │                           │ or      │           │
        │                           │ /block  │           │
        │                           └─────────┘           │
        │                                                  │
        └──────────────────────────────────────────────────┘
```

### Flow 4: Settings Navigation

```
   ┌────────────┐
   │  /settings │ (settings/index.html — overview hub)
   │            │
│ [Profile]  │──► /profile              — view/edit kind-0 metadata
│ [Relays]   │──► /settings/relays      — relay list CRUD
│ [Security] │──► /settings/security    — change passphrase, reveal nsec, logout
│ [Follows]  │──► /settings/follows     — view/unfollow users
│ [Blocks]   │──► /settings/blocks      — view/unblock users
│ [Backup]   │──► /settings/backup      — export/restore encrypted backup
   └────────────┘
```

### Flow 5: Relay Management

```
   ┌───────────────────┐
   │ /settings/relays   │
   │                    │
   │ Read Relays:       │    ┌──────────────────┐
   │  wss://a.com  [×] │    │ Add Relay        │
   │  wss://b.com  [×] │───►│ wss://...        │
   │                    │    │ ☑ Read  ☑ Write  │
   │ Write Relays:      │    │ [Add]            │
   │  wss://a.com  [×] │    └────────┬─────────┘
   │  wss://c.com  [×] │             │
   │                    │             ▼
   │ [Save & Publish]  │    relay added to list
   │                    │    "Unsaved Changes" appears
   └────────┬──────────┘
            │
            │ click "Save & Publish"
            ▼
   ┌────────────────────┐
   │ POST /relays/publish│
   │ persist server-side│
   │ publish kind-10002 │
   │ to all write relays│
   └────────────────────┘
```

### Flow 6: Security Settings

```
   ┌──────────────────────────┐
   │ /settings/security        │
   │                           │
   │ ┌─ Change Passphrase ──┐  │
   │ │ Current: [••••••••]  │  │
   │ │ New:     [••••••••]  │  │
   │ │ Confirm: [••••••••]  │  │
   │ │ [Update Passphrase]  │  │
   │ └──────────────────────┘  │
   │                           │
   │ ┌─ Export Identity ────┐  │
   │ │ Reveal nsec private  │  │
   │ │ key                  │  │
   │ │        [Reveal nsec] │  │
   │ └──────────────────────┘  │
   │                           │
   │ ┌─ Danger Zone ────────┐  │
   │ │ Logout               │  │
   │ │           [Logout]   │  │
   │ └──────────────────────┘  │
   └──────────┬───────────────┘
              │
              ├── "Reveal nsec" clicked
              │   ┌──────────────────────┐
              │   │ Password modal       │
              │   │ "Enter passphrase"   │
              │   │ [••••••••] [Confirm] │
              │   └──────────┬───────────┘
              │              │ correct passphrase
              │              ▼
              │   ┌──────────────────────┐
              │   │ nsec displayed       │
              │   │ nsec1qz2x3y4...      │
              │   │ [Copy] [Download]    │
              │   └──────────────────────┘
              │
              └── "Update Passphrase" clicked
                  │ verify current decrypts nsec
                  ▼
              ┌──────────────────────┐
              │ Client-side:         │
              │ 1. decrypt nsec with │
              │    old password      │
              │ 2. derive new key    │
              │ 3. re-encrypt nsec   │
              │ 4. update localStorage│
              └──────────────────────┘
```

### Flow 7: Logout

```
   Any Page
      │
      │ click "Logout"
      ▼
   ┌─────────────────────┐
   │ Confirm dialog:     │
   │ "This will clear    │
   │  your session."     │
   │ [Cancel] [Logout]   │
   └──────────┬──────────┘
              │ confirm
              ▼
   ┌─────────────────────┐
   │ POST /auth/logout   │
   │ → revoke session    │
   │ → clear session     │
   │   cookie            │
   └──────────┬──────────┘
              │
              ▼
   ┌─────────────────────┐
   │ Redirect to /login  │
   └─────────────────────┘
```

---

## Page Descriptions

### Onboarding Wizard (P2–P7)

The wizard is a single-page application within a traditional page: step changes are rendered as HTMX fragments swapped into a container. The URL only changes on the first step (`GET /onboarding`) and completion (`/onboarding/welcome`). Intermediate steps use HTMX fragment swaps with `hx-push-url="false"`.

**Back navigation**: each step stores form data in sessionStorage so revisiting a previous step preserves input.

**Step 1 — Method Choice** (`onboarding/step-method.html`):
- Two cards: "Create New Key" and "Import Key"
- Import shows a textarea for nsec paste
- On submit: POST `/onboarding/step-method` with `{method, nsec?}`
- Response: profile step fragment

**Step 2 — Profile Setup** (`onboarding/step-profile.html`):
- Fields: username, display name, about, picture (Blossom file upload), banner (Blossom file upload), Lightning address, website
- NIP-05 is NOT a form field — it is automatically derived from the username as `{username}@{bottin-domain}`
- A read-only badge shows the auto-derived NIP-05 below the username field (e.g., "NIP-05 will be auto-registered as alice@bottin.example.com")
- Avatar and banner use file upload with drag & drop area; files are uploaded to the configured Blossom server (NIP-96), and the returned URL is stored in the profile
- The Blossom server URL is set via `BLOSSOM_URL` environment variable and shown as a small badge below the form
- Username field debounces 500ms and calls `GET /api/v1/resolve/{username}` for availability
- On submit: POST `/onboarding/step-profile`
- Response: security step fragment

**Step 3 — Security** (`onboarding/step-security.html`):
- Fields: password (min 8 chars, strength meter), confirm password
- On submit: POST `/onboarding/step-security`
- Response: confirm step fragment

**Step 4 — Confirm** (`onboarding/step-confirm.html`):
- Read-only review card showing all entered data
- "Back" button returns to previous step
- Submit sends POST `/onboarding/complete`
- On success: redirect to `/onboarding/welcome`

**Welcome** (`onboarding/step-welcome.html`):
- Shows nsec (masked by default, reveal button, copy to clipboard)
- Checkbox: "I have saved my backup key"
- "Continue" button navigates to `/search`

### Login (P8)

A standalone page with an nsec input field. Flow:
1. User pastes nsec
2. Client derives npub and calls `POST /api/v1/auth/init` with npub
3. Server returns challenge
4. Client signs NIP-98 event locally using Web Crypto
5. Client calls `POST /api/v1/auth/complete` with the signed proof
6. Server sets session cookie, redirects to `/search`

The "Restore from backup" link navigates to `/restore`.

### Search (P10)

A search page with:
- Search input (text field with search icon)
- Results list (each item shows: avatar, display name, name, NIP-05, follow/block action buttons)
- Empty state ("Search for profiles" before query, "No results" for empty result)
- Loading state (skeleton placeholders during query)

Input fires `GET /api/v1/search?q=...&limit=20` with 300ms debounce.

### Profile (P11, P12)

**Own profile** (`GET /profile`):
- Display name, username, avatar, banner, about, website, NIP-05, Lightning address
- Edit button toggles inline edit mode
- Copy npub button
- Not editable: npub, pubkey

**Other user's profile** (`GET /profile/{pubkey}`):
- Same read-only display
- Follow/Unfollow button (toggles)
- Block/Unblock button (toggles)

### Settings Hub (P13)

An overview page with cards/links to each settings section:
- **Profile**: Edit your display name, bio, avatar
- **Relays**: Manage Nostr relay connections (read/write)
- **Security**: Change encryption passphrase
- **Follows**: View and manage followed users
- **Blocks**: View and manage blocked users
- **Backup**: Export encrypted backup file

### Relay Management (P14)

Two columns (or lists) for read relays and write relays. Each relay entry shows the URL and a remove button. An "Add Relay" section at the bottom has:
- URL input (`wss://` validation)
- Read checkbox (default: checked)
- Write checkbox (default: checked)
- Add button

A "Save & Publish" button appears when there are unsaved changes. On click:
1. POST `/api/v1/relays` for each new relay
2. DELETE `/api/v1/relays` for each removed relay
3. POST `/api/v1/relays/publish` to persist and publish NIP-65 kind-10002

### Security / Passphrase Change (P15)

Two sections on one page:

**1. Change Passphrase**
- Three fields: current password (validated by attempting to decrypt the nsec), new password (min 8 chars, strength meter), confirm
- All processing is client-side JavaScript using Web Crypto API
- Re-encrypts and updates localStorage atomically

**2. Export Identity — Reveal nsec**
- "Reveal nsec" button opens a password modal (same pattern as imani-apps)
- User enters their encryption passphrase to authenticate
- On success, the nsec is displayed in a yellow warning card with copy and download buttons
- The nsec never leaves the browser; the modal is rendered entirely client-side

---

## Shared Layout

All pages share a common Thymeleaf layout template (`layout.html`):

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Bottin Client</title>
    <link rel="stylesheet" href="/css/styles.css">
    <script src="/js/htmx.js"></script>
</head>
<body>
    <nav th:replace="~{fragments/nav :: nav}"></nav>
    <main th:remove="tag">
        <div th:replace="${content}"></div>
    </main>
    <script src="/js/app.js"></script>
    <script src="/js/nostr-crypto.js" defer></script>
    <script src="/js/nap-client.js" defer></script>
</body>
</html>
```

Authenticated pages additionally load `/js/settings-relays.js` (only on settings pages).
