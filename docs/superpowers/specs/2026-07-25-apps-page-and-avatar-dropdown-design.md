# Apps Page and Avatar Dropdown

## Summary

Replace the post-login landing page from `/search` to a new `/apps` page, and add an avatar dropdown menu to the navigation header with Profile and Logout options.

## Changes

### 1. New `/apps` page

- **Controller**: `AppsController` serving `GET /apps` → renders `layout` with `content = "apps"`.
- **Template**: `apps.html` — empty placeholder fragment (`th:fragment="content"`), ready for future content.

### 2. Redirect after login

- **`login.html`**: Change both JS redirects (nsec paste mode and passphrase unlock mode) from `'/search'` to `'/apps'`.
- **`router.html`**: Change session-redirect from `/search` to `/apps`.

### 3. Avatar dropdown in nav

- **`nav.html`**: Replace the current avatar `<a href="/profile">` with a dropdown toggle.
- The avatar `<img>` stays the same. Clicking it toggles a dropdown below it.
- Dropdown items: **Profile** (`/profile`), **Logout** (existing HTMX-based logout).
- Clicking outside or on an item closes the dropdown.
- Existing nav links (Search, Settings) remain unchanged.
- Minimal CSS (inline or in an existing stylesheet) for dropdown positioning and visibility toggle. No JS framework dependency — plain CSS click handler via a checkbox or a simple JS toggle.

### 4. No other changes

No new dependencies, no Spring Security config changes, no additional model attributes.
