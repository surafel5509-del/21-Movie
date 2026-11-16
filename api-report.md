# Live API verification

- Keyed request: HTTP 403
- UA matrix (plain / Mozilla / app-prefix / app-full): 403 / 200 / 200 / 200
- Keyed + Mozilla UA: 200
- Keyed + app UA: 200
- Rate-limit tier: none
- infoHash fields: 0
- magnetUrl fields: 0

First 200 chars of the response:

```
Forbidden
```
