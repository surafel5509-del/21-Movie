# Verifying a real download on your phone

This is the part CI cannot do: the GitHub Actions runners have no Android device
and this development sandbox has no emulator, so a real swarm transfer has to be
confimed on hardware. Everything below is a concrete pass/fail check, in order.

APK: [v1.1.0 release](https://github.com/surafel5509-del/21-Movie/releases/tag/v1.1.0)
(`TorrentClawMovies-v1.1.0-release.apk`, ~19 MB, debug-signed so it installs
directly).

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

## 2. Unlock downloads (required)

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

## 5. Finish and play

For a fast test pick a small release (a few hundred MB, high seed count).

| Check | Expected |
| --- | --- |
| Progress reaches 100 % | row moves to **Completed** |
| Completion notification | "Download complete" |
| Tap the ▶ (play) button on the completed row | a video player opens and plays the movie |
| File location | `Android/data/com.torrentclaw.movies/files/Movies/` |

If play does nothing: no video player is installed for that container (`.mkv`
needs VLC or Just Player; `.mp4` works with most). Install VLC and retry.

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
| 49 unit tests pass, including engine contracts | download speed on your network |
| 3 ABIs packaged (arm64-v8a, armeabi-v7a, x86_64) | background survival over hours |
| Release APK builds and is signed | battery/thermal behaviour |
| Mapping of real API payloads (anonymous + keyed) | your ISP's BitTorrent policy |

The app does not claim a download has been proven until §5 passes on a real
device.
