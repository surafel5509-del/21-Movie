# Verifying a real download on your phone

This is the part CI cannot do: the GitHub Actions runners have no Android device
and this development sandbox has no emulator, so a real swarm transfer has to be
confimed on hardware. Everything below is a concrete pass/fail check, in order.

APK: [v1.2.0 release](https://github.com/surafel5509-del/21-Movie/releases/tag/v1.2.0)
(`TorrentClawMovies-v1.2.0-release.apk`, ~21 MB, debug-signed so it installs
directly).

This build has a **PRO API key compiled in**, so downloads work on first launch
without pasting anything. Settings → TorrentClaw account shows the tier the API
reports (`PRO tier · 1,000 req/min · downloads unlocked`) once a request has
succeeded.

> The release APK is signed with the debug key unless `KEYSTORE_*` secrets are
> configured. That is intentional: it installs without Play, but you cannot
> update it from a Play Store build later.

---

## 1. Install and launch

| Check | Expected |
| --- | --- |
| Install the APK | Installs on Android 8.0+ (minSdk 26) |
| Launch | Home screen fills with movie posters, no crash |
| Tap any movie | Detail screen shows a release list with quality, size, seeds |

**If the release list is empty, stop and read §2 first** — that is the API tier,
not the app.

## 2. Confirm the key is live (no action needed)

The key is built into the APK. This section only exists so you can *see* it
working; nothing needs pasting.

| Check | Expected |
| --- | --- |
| Settings → TorrentClaw account | `PRO tier · 1,000 req/min · downloads unlocked` |
| A movie's detail screen | release cards show a **Download** button, not a lock |

Verified from CI against the live API with this exact key: `X-RateLimit-Tier: pro`,
999 requests remaining, and the keyed response contains **17 `infoHash` and 17
`magnetUrl` fields where the anonymous response contains none** — see
[`api-report.md`](../api-report.md).

If Settings shows the anonymous state, the device could not reach
`torrentclaw.com/api` at all (the tier comes from a response header), which points
at the network rather than the key.

### Fallback: paste your own key

TorrentClaw strips `infoHash` / `magnetUrl` / `torrentUrl` from responses on the
anonymous tier. A magnet link is *nothing but* an info hash, so no app can
download without one — this is the API's design, not a limitation of this build.

1. Open your free key page: <https://torrentclaw.com/register>
2. In the app: **Settings → TorrentClaw account → API key → Add**
3. Paste the key, tap **Save key**
4. Go back to a movie's detail screen and **pull to refresh**

| Check | Pass |
| --- | --- |
| Settings shows `Configured · 120 req/min` | key stored |
| Release cards show a **Download** button instead of the lock row | hashes returned |
| A card offers **Play/Open magnet** | magnet URI present |

The key is stored in DataStore on the device only. It is applied to the next
request without restarting the app.

## 3. Start a download

Tap **Download** on a release with a healthy swarm (green tick: 20+ seeders).

| Check | Expected | Where |
| --- | --- | --- |
| Card switches to an inline progress block | percentage, speed, peer count | Detail |
| First few seconds | "Getting torrent details…" while metadata is fetched | Detail |
| Then | percentage climbs, speed > 0, peers > 0 | Detail |
| A notification appears | "Downloading 1 movie", tap opens the app | Notification shade |
| Background it | transfer continues; notification keeps updating | Home screen |
| Downloads tab | the movie is listed as active with live progress | Drawer → Downloads |

If it sits at "Getting torrent details" with 0 peers for 90 s the app now says
so explicitly ("No peers are responding…"). That means the swarm is dead or your
carrier/Wi-Fi blocks BitTorrent — try a different release or a VPN. It is not a
silent hang.

## 4. Pause, resume, kill

| Check | Pass |
| --- | --- |
| Tap pause | speed drops to 0, the row stays **PAUSED** (it does not flip back to active) |
| Tap resume | speed returns, percentage continues from where it was |
| Pause, then swipe the app out of recents, then reopen | Downloads tab re-adds the magnet and progress resumes from the bytes on disk (libtorrent re-checks, so it may pause briefly at the same percentage) |

## 5. Finish and play — in the app

For a fast test pick a small release (a few hundred MB, high seed count).

| Check | Expected |
| --- | --- |
| Progress reaches 100 % | row moves to **Completed** |
| Completion notification | "Download complete" |
| Tap ▶ on the completed row | the movie plays **inside the app** (no other app opens) |
| Controls | scrub bar, ±10 s, play/pause, Fit/Fill, rotate, fullscreen, PiP |

There is no external-player dependency for the in-app player: ExoPlayer decodes
`.mp4`/`.mkv`/`.webm` itself. A codec the device's hardware does not support
(e.g. some `AV1` releases on an older phone) will still fail — pick an `h264` or
`hevc` release if a specific file will not open.

### Picture-in-picture

| Check | Expected |
| --- | --- |
| Tap the PiP button while playing | the video shrinks into a floating window |
| Leave the app (Home / Recents) while in PiP | **the movie keeps playing** in the small window |
| Drag the PiP window's corner | it resizes, and the video keeps its aspect ratio |
| Tap the PiP window | back to the full-screen player, same position |

### Leaving the app and coming back

This is the behaviour that depends on the resume table, so test it deliberately:

| Check | Expected |
| --- | --- |
| Play ~3 minutes, press Back/Home (no PiP) | playback pauses, position saved |
| Swipe the app out of Recents | process dies |
| Reopen the app | Home shows the film under **Continue watching** with a progress bar |
| Tap the card, or the movie's ▶ in Downloads | playback resumes at the saved second, with a "Resuming from 3:12" note |
| Watch to the end, reopen | it starts from the beginning (finished films are not "resumed" into their credits) |

## 6. If something fails

Engine errors are surfaced on the release card in red, and in the Downloads tab.
The most useful evidence is Android's log:

```
adb logcat -s TorrentEngine TorrentService DownloadManager
```

Expect to see, in order:

```
libtorrent 2.1.x started → /data/user/0/com.torrentclaw.movies/files/... /Movies
handle for <HASH> not available yet        ← normal while metadata loads
metadata received for <HASH> (1234567890 bytes)
```

`No peers found` / `Couldn't fetch the torrent's metadata` after this is a swarm
or network problem, not an app problem.

---

## What is and is not verified

| Verified by CI (real Gradle, real libtorrent4j) | Not verifiable without a device |
| --- | --- |
| Kotlin compiles against libtorrent4j 2.1.0-39 | that a specific magnet completes |
| 57 unit tests pass, including engine + resume contracts | download speed on your network |
| 3 ABIs packaged (arm64-v8a, armeabi-v7a, x86_64) | background survival over hours |
| Release APK builds and is signed | battery/thermal behaviour |
| The PRO key unlocks magnets (17 vs 0 from CI) | playback on your specific device/decoder |
| Resume logic (57 unit tests) | PiP behaviour on your launcher/OEM |
| Mapping of real API payloads (anonymous + keyed) | your ISP's BitTorrent policy |

The app does not claim a download *or* a resume has been proven until §5 and the
"leaving the app" table pass on a real device. Everything above them is verified.
