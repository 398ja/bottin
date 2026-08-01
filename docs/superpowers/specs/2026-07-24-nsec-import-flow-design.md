# nsec import flow

Streamlines importing an existing Nostr identity into two steps — enter the
nsec, then set and confirm a passphrase — instead of routing imports through the
full four-step create wizard (which pointlessly asks an imported key to set up a
new profile).

## Flow

1. **Enter the nsec** — the existing method screen. Choosing "Import Existing
   Key" and pressing Continue validates that the nsec decodes to a real `nsec`
   and branches to step 2, skipping profile/confirm. "Create New Key" keeps its
   current four-step path.
2. **Set & confirm passphrase** — a new `step-import` screen. On "Import & Sign
   In": `buildEncryptedIdentity(nsec, passphrase)` → `APP.saveIdentity`, then an
   automatic NAP sign-in with the imported key, then redirect to `/search`
   authenticated. A Back button returns to step 1 with the nsec retained.

## Changes

- **`app.js`** — extract the NAP challenge/sign/complete handshake into
  `APP.napLogin(hexKey, npub)` so login and import share one implementation.
- **`login.html`** — call `APP.napLogin` instead of its local copy.
- **`OnboardingController`** — add `import` to the `/onboarding/step/{step}`
  whitelist so `step-import` renders.
- **`step-method.html`** — Continue branches: import → validate nsec →
  `htmx.ajax('GET', '/onboarding/step/import')`; create → `.../step/profile`.
- **`step-import.html`** (new) — passphrase + confirm with mismatch and strength
  meter, a two-step indicator, "Import & Sign In", and a Back button.

## Decisions

- **Auto sign-in after import** — land the user in `/search`, authenticated,
  with no extra login step.
- **No profile/NIP-05 step for import** — an imported key already has its
  profile on the network.
- **Create flow unchanged** — it still ends on the welcome/backup screen and does
  not auto-login, so a new-key user is nudged to save their backup first. Making
  create auto-login too is a possible follow-up.

## Testing

Browser JS + a thin controller route, with no JS test harness in the repo, so the
import path is verified live in the running app; the Java reactor stays green.
