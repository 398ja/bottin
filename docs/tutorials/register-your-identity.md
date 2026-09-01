# Register Your Nostr Identity

This tutorial walks you through creating an identity on a Bottin deployment. By
the end you will have a handle that resolves over NIP-05, a Nostr key that never
leaves your browser, and a backup of that key.

It takes about two minutes. You need nothing beforehand — no existing key, no
email address, no invitation.

## What you are about to create

A Nostr identity is a cryptographic key pair. Bottin generates one in your
browser and encrypts it with a password you choose. The deployment never sees
your key, and it never sees your password.

That has a consequence worth understanding before you start: **nobody can reset
your password or recover your key for you.** There is no "forgot password" link,
because there is nothing on any server to reset. This is why the last step of
this tutorial saves a backup.

## Step 1: Open the deployment and choose Register

Open the client in your browser — for a local deployment that is usually
`http://localhost:8082`. You will be offered two options:

- **Register** — create a new identity. Choose this one.
- **Login** — sign in with a Nostr key you already have.

Select **Register** and press **Continue**.

## Step 2: Choose a handle and a password

You are asked for three things, and only three:

1. **Handle** — the name people will find you by, for example `alice`. Use
   lower-case letters, digits, hyphens and underscores. As you type, Bottin
   checks whether it is still free and tells you underneath the field.
2. **Password** — at least 8 characters. A strength indicator appears as you
   type. This encrypts your key in this browser.
3. **Confirm password** — the same password again.

Press **Create account**.

Behind the scenes Bottin generates your key, claims your handle in the
directory, and only then encrypts the key and stores it. If somebody else takes
the handle in the moment between your typing it and your pressing the button,
you are simply asked to pick another — nothing has been created yet, and your
password is kept.

## Step 3: Save your backup key

You are shown your **nsec** — your private key, in the standard Nostr format.

This is the only copy that exists outside your browser's storage. If you clear
your browser data, change device, or forget your password, this string is the
only way back into your identity.

Press **Copy** or **Download**, and put it somewhere you would keep a password
or a recovery code. Then tick **I have saved my backup key** and press
**Continue to Search**.

> Anyone holding your nsec controls your identity completely. Store it as you
> would store the key to a safe — never paste it into a chat, an email, or a
> website.

You are now signed in.

## Step 4: Fill in your profile

You registered with a handle alone, so other people currently see you by that
handle. To add a display name, a picture, and anything else, open
**Profile → Edit**.

A reminder appears at the top of the page until you do this, or until you
dismiss it. On the edit page you can set:

- Display name and a short "about" description
- An avatar and a banner image
- A lightning address and a website

Press **Save & Publish**. You will be asked for your password once, so that the
update can be signed with your key, and it stays unlocked for the rest of the
session.

## What you have now

- A handle that resolves over NIP-05, so `you@thedeployment` works in any Nostr
  client
- A key encrypted in your browser with your password
- A backup of that key, saved somewhere safe
- A published profile, if you completed step 4

## Where to go next

- [Upload a Profile Avatar and Banner](../how-to/upload-profile-images.md) — the
  detail on images and media servers
- [Integrate NIP-05 Validation](../how-to/integrate-nip05-validation.md) — how
  the handle you just claimed is served and verified
