# Gramo

A minimal monochrome music player for the Light Phone III, built as a native
[Light SDK](https://github.com/lightphone/light-sdk) tool. Gramo plays your own
music library — starting with the songs you uploaded to YouTube Music, exported
via Google Takeout — with no Google apps, accounts or services on the phone.

## Install

Prebuilt APKs land on the [releases page](../../releases) whenever a `v*` tag
is pushed (GitHub Actions builds `tool-release.apk` and attaches it as
`Gramo-vX.Y.Z.apk`). Obtainium can track this repo directly: add source
`https://github.com/DanVlf/Gramo` — the latest release APK installs like any
update over previous versions.

Version numbers come from `tool/lighttool.toml`: bump `versionCode` (and
`versionName`) before tagging, since Android refuses downgrades.

## Sync

Keep a music folder on your computer in step with the phone:

1. On the computer, run the bundled server in your music folder:

   ```sh
   python3 scripts/serve_music.py ~/Music 8765
   ```

2. In Gramo: IMPORT → SYNC URL, enter
   `http://<computer-ip>:8765/gramo.json` → SAVE AND SYNC.

Gramo remembers the URL, and every time it opens it downloads any tracks
that are new on the computer (comparing by file name). SYNC NOW forces a
check. Local-network plain-http URLs are allowed for this; an https URL
works too. Sync only ever adds tracks — it never deletes from the phone.

## Importing Music

**From Google Takeout (HTTPS download)**

1. On a computer, go to [takeout.google.com](https://takeout.google.com)
2. Deselect all, then select **YouTube and YouTube Music → Music uploads** (your
   uploaded songs come back as original files)
3. Choose **.zip** export, and either:
   - copy the download link into Gramo (IMPORT → ARCHIVE URL). Takeout links can
     require the cookie header of your logged-in browser session — paste it
     into the optional COOKIE field; or
   - re-host the zip anywhere plain HTTPS (your own server, a GitHub release,
     a USB-less sync target...) and use that link.
4. Gramo downloads, unzips and adds every audio file to the library.

**From the Tool Manager (local wifi)**

Gramo is Tool-Manager-capable: start the Tool Manager on the phone
(Settings), open it in a browser on your computer, and drop `.zip` files into
Gramo's upload folder. Then IMPORT → IMPORT UPLOADED ARCHIVES.

Supported audio formats: mp3, m4a, aac, flac, wav, ogg, oga, opus.
Archives must be `.zip` (Takeout's default); each split part of a Takeout
export imports independently.

## Playing

Playback uses the SDK's detached audio session, so music keeps playing with
the tool in the background, with media controls and the LightOS volume
experience. No analytics, no ads, no network use during playback — files live
in the tool's private storage.

## Building

```sh
./gradlew :tool:assembleDebug
./gradlew :tool:test
```

For the LightOS emulator, switch `serverPackage` in `tool/lighttool.toml` to
`com.thelightphone.sdk.emulator` and build with
`-DlightSdk.allowAltServerPackage=true` (revert to `com.lightos` before
submitting through the dashboard).

## Credits

Built on the [light-sdk](https://github.com/lightphone/light-sdk) scaffold.
Music is the user's own; Gramo does not touch YouTube or Google services.
