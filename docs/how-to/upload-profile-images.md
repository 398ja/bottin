# Upload a Profile Avatar and Banner

This guide explains how to set your profile avatar and banner from a local file
and how to point the client at the media server that stores them.

## Choose an image

1. Open `/profile/edit` (or reach the profile step during onboarding).
2. Under **Avatar** or **Banner**, choose a file from your device.
3. The image appears immediately as a local preview while it uploads.
4. If your session is locked, the unlock prompt appears: the upload is
   authorised by an event signed with your own key, so the key must be available.
5. On success a toast confirms the upload and the preview switches to the stored
   image. On `/profile/edit`, press **Save & Publish** to publish the new kind-0
   profile event to your write relays.

Images must be an image type and 5 MB or smaller. Larger or non-image files are
refused before anything is sent.

**Remove** clears the image from your profile. The uploaded file stays on the
media server, where you can delete it with your own key.

## Configure the media server

The client uploads to a [Blossom](https://github.com/hzrd149/blossom-server)
server using BUD-01 authorisation and a BUD-02 `PUT /upload` request. Set its
base URL with `BOTTIN_BLOSSOM_URL`:

```bash
BOTTIN_BLOSSOM_URL=https://blossom.example.com
```

The bundled `docker-compose.yml` runs one locally, published on
`${BOTTIN_BLOSSOM_PORT:-8888}`.

The upload goes straight from your browser to that server: no image bytes pass
through Bottin, and every blob is owned by your pubkey, so you can delete it
later with your own key.

## Troubleshoot

| Symptom | Cause |
|---|---|
| "Choose an image file." | The selected file is not an image type. |
| "Image must be 5 MB or smaller." | The file exceeds the size cap. |
| "Upload rejected: …" | The server refused the request; the text is its `X-Reason`. |
| "Upload failed: …" | The server could not be reached. Check `BOTTIN_BLOSSOM_URL`. |
| Nothing happens after the unlock prompt | The prompt was cancelled; the previous image is kept. |
