# Live TorrentClaw API verification

Run on GitHub Actions against the live API, using the same User-Agent
and headers the app sends.

| Probe | Result |
| --- | --- |
| Anonymous search - infoHash fields | 0 |
| Keyed search - HTTP | 200 |
| Keyed search - X-RateLimit-Tier | pro |
| Keyed search - remaining | 999 |
| Keyed search - infoHash fields | 17 |
| Keyed search - magnetUrl fields | 17 |
| Keyed .torrent endpoint - HTTP | 404 |

**PASS**: the key unlocks info hashes and magnets, so in-app
BitTorrent downloads can start.
