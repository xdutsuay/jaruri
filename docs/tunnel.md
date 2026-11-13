# Tunnel (v1): local DNS filter — `tunnel` flavor only

Tunnel lets Jaruri see and filter the **DNS lookups** apps make, through a local
`VpnService`. It runs entirely on the phone: there is no remote server, no
account, and nothing is uploaded.

## Two builds

| Flavor | Default | INTERNET | Tunnel code / card | Who builds it |
|---|---|---|---|---|
| `offline` | yes | **no** | absent (hub card hidden, no tunnel classes in the APK) | F-Droid, everyday use |
| `tunnel` | no | yes (+ `ACCESS_NETWORK_STATE`) | present | built by hand: `./gradlew :app:assembleTunnelDebug` |

Both flavors share one `applicationId`, so they install over each other (and share data).
`versionName` gets a `-tunnel` suffix. The tunnel's Room tables (rules, lists) are in the
shared schema (DB v14) so the two builds can swap without a migration problem; the offline
build simply never uses them.

Checks that keep this honest:
- `OfflineFlavorTest`: no `FlavorFeatures.tunnel`, hub card gone, tunnel classes absent.
- `NoNetworkOutsideTunnelTest` (both flavors): no sockets/URLs/HTTP clients outside `src/tunnel`.
- `aapt2 dump permissions` of both APKs is kept in `/workspace/jaruri-listener/evidence/`.

## How it works

- The VPN interface routes **only** a virtual resolver: `10.111.222.1` and `fd66:6a61:7275::1`
  (as /32 and /128). Android hands that resolver to apps as their DNS server. All other
  traffic stays off the tunnel, so speed and battery cost are tiny and nothing else is touched.
- Each UDP query is parsed and decided in this order: **paused → app rule → your Allow
  rules → your Block rules → enabled lists**.
- Blocked: `NXDOMAIN` (default) or `0.0.0.0` / `::` (setting). Allowed: forwarded to
  **the current network's DNS servers** (Wi-Fi/ethernet preferred, never the VPN itself) on
  `protect()`ed sockets bound to that network. No answer within 2.5 s → `SERVFAIL`.
- DNS over TCP to the virtual resolver gets a TCP reset, so resolvers fall back to UDP.
- Jaruri itself is excluded from the VPN, so its own lookups can never loop.
- Not a foreground service: an established VPN already has foreground priority. An ongoing
  notification shows status with **Pause 10 min / Resume / Stop**; a Quick Settings tile toggles it.
- Pause makes the filter pass everything through until the time is up (no VPN restart).

### Per-app rules
- **Bypass**: the app is left out of the VPN (`addDisallowedApplication`). Reliable.
- **Block: best effort** (labelled so in the UI): blocks lookups when Android tells us which
  app asked (`getConnectionOwnerUid`, Android 10+). Many phones resolve through a shared
  system resolver, which then shows up as "unknown app", and apps can also use cached
  addresses or their own DoH. Strict per-app blocking would need a full packet tunnel (v2).

### Lists
- **Bundled: StevenBlack unified hosts** (snapshot 2026-11-08, 78,215 domains), on by
  default, switchable. Stored gzipped (`assets/blocklists/stevenblack-hosts.gzip`, 591 KB; the
  extension is not `.gz` because AGP inflates `.gz` assets at build time). Parsed off the
  main thread and cached. A 200k-domain matcher test bounds lookup time.
- Import your own hosts/plain-domain lists (up to 1M entries each); exact and `*.suffix` rules.

## Log and retention
- `tunnel_log.db` (separate from the main DB): time, app (when known), name, type,
  decision, rule, latency. **7 days** of detail (cap 100k rows), **90 days** of daily counts.
  Pruned daily by WorkManager and at every start.
- Excluded from both backup rule sets (`backup_rules.xml`, `data_extraction_rules.xml`).
- Export the log as CSV (formula-injection guarded) or Markdown via the share sheet.

## Licences: read before distributing the tunnel build
StevenBlack's unified list is MIT, but it **merges sources with their own terms**
(see `assets/blocklists/STEVENBLACK-NOTICE.txt`, shown in-app):
- MVPS hosts: CC BY-NC-SA 4.0 (non-commercial)
- Dan Pollock's hosts: non-commercial use with attribution
- KADhosts: CC BY-SA
- yoyo.org (Peter Lowe): no licence stated

This is fine for personal use, but it is **not "free" in F-Droid's sense**. F-Droid builds the
`offline` flavor (no list, no tunnel), so it is unaffected. Before publishing the tunnel build
anywhere, either drop those sources (StevenBlack publishes per-source files) or ship no
default list.

## Size
Debug APKs at 39bf3ec: offline 15,290,734 B, tunnel 16,714,618 B (+1.36 MiB). About
0.59 MB is the list asset; the rest is unminified debug dex/resources for the tunnel code,
which R8 shrinks a lot in release.

## Known limits (v1)
- Apps or browsers using their own encrypted DNS (DoH/DoT, e.g. Chrome "Secure DNS",
  Firefox DoH) bypass the filter. Android **Private DNS = strict hostname** also bypasses it;
  the Status screen warns about that.
- Only one VPN can be active; starting another VPN turns Tunnel off ("Revoked").
- Per-app Block is best effort (see above); attribution varies by phone.
- Domains, not URLs: no content filtering and no HTTPS inspection, by design.
- `VpnService.Builder` cannot run under Robolectric, so the real interface, upstream
  forwarding and attribution are verified only on a device (see the evidence checklist).
  The packet loop itself is tested end-to-end with a fake tun and a local stub DNS server.
