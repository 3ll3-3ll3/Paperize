# PikPak cloud gallery integration design

## Status
Architecture investigation and provider-neutral groundwork only. **NO real PikPak authentication or account API operation has been completed.** No premium bypass, device spoofing or CAPTCHA avoidance is proposed.

## Candidate paths
| Path | What is confirmed | Risk / status |
| --- | --- | --- |
| Official Connected Apps / authorization | PikPak describes Connected Apps and read-only permissions, and offers PATs intended for personal unattended scripts. See [official FAQ](https://mypikpak.com/en-US/faq), [permissions](https://pikpak.me/en-US/help-center/connected_apps/managing_connected_apps/connected_app_permissions). | Need official documented mobile application OAuth / file list / download contract, registration requirements and test account. **Unverified** |
| rclone PikPak Go backend | `rclone/rclone/backend/pikpak/pikpak.go`, `helper.go`, `api/types.go` implement sign-in/captcha, paged lists, file IDs, expiring links and retry logic. Docs [rclone.org/pikpak](https://rclone.org/pikpak/). | rclone uses a different sign-in integration; endpoints and auth mechanisms are NOT evidence that official PATs work with them. No copy-paste Go into Kotlin. Compatibility/terms **unverified**. |
| SAF DocumentProvider | `AndroidDocumentSource.kt` already supports Android's persisted SAF grants. | Works only if a third-party provider actually exposes user-approved PikPak directories. Whether PikPak itself provides one is **unverified**. |
| WebDAV | Generic DocumentProvider could expose it. | May have availability/paywall constraints; not chosen as free-default. |

Official [pikpak-open](https://github.com/pikcloud/pikpak-open) SDK materials include a specific Telegram SDK, **not** proven general-purpose Android photo browsing SDK. No privileged API assumptions.

## Chosen architecture
1. Pure Kotlin `domain/cloud` includes provider-neutral `CloudFileGateway`, `CloudGalleryCrawler`, paging contracts, and metadata.
2. Local **metadata only** Room index, separate from the upstream library DB to avoid risky migrations. Indexed file ID, parent folder ID, mime, filename, size, modified, revision; 200-500 records per transaction. No full-resolution images on folder selection.
3. `CloudImageCache` stores only on-demand image bytes under **app-private** `cacheDir/cloud-wallpapers`. Default 500 MiB limit, LRU eviction, revision-key invalidation, atomic download, bounded per-image bytes. Never touch public gallery.
4. Approved provider adapter implements `CloudFileGateway` for paged folder listing + streamed read-only download. Factory must fail closed until actual mobile auth and endpoints verified; UI must not claim logged-in otherwise.
5. Worker: paging, iterative recursive walk, visited-folder guard, exclusions, cancellation, and generation markers. Persist each page. Only delete absent rows when **all pages of all selected roots** succeed; retain prior index on any error.
6. At rotation: database selects candidate / checks exclusion + recent IDs; cache hit -> renderer, miss -> single authorized image download into private cache -> bitmap decode/scaling -> `WallpaperController` commit -> optional next-item prefetch on Wi-Fi.
7. Wi-Fi-only prefetch and optional mobile data on-demand, exponential backoff for 429/5xx and no retries for revoked authorization. Background synchronization follows Android WorkManager quotas.
8. Credential lifecycle: use system browser authorization + PKCE when a supported OAuth flow is *confirmed*; Keystore-protected storage for sensitive session material, no plain DataStore tokens, never include secrets in logs, PRs or artifacts. Revoke/logout clears private credentials and cache.

## Scale and invariants
- 1k/10k/50k photos and 100GB+ total are **metadata indexing problems**. Never call `download` from listing/crawl.
- Deduplicate by immutable file ID and provider/account namespace; avoid recursive Kotlin call stack overflow.
- Cache key uses provider/account + file ID + version or ETag, hashed to prevent path traversal.
- Token-bearing signed URLs may expire; re-resolve links on cache misses; never persist URLs as durable metadata.
- Network and provider limits apply; obey quotas rather than bypassing them. Official FAQ currently states free Connected Apps share a 5GB/day downstream cap in addition to monthly quota.
- Keep photos private; do not export user data to self-hosted endpoints.

## Integration with upstream code
Reference points in `docs/architecture-analysis.md`: `WallpaperRenderer.kt` URI decoder seam, `WallpaperController.kt` synchronized HOME+LOCK behavior, `WallpaperRepositoryImpl.kt` full queue shuffle, `AlbumRefreshWorker.kt` local-only refresh. Preserve existing `WallpaperSourceType.DIRECT/FOLDER`; distinguish remote via new provider-specific namespace and metadata.

## Remaining verification gates
1. Identify PikPak's supported first-party mobile app authorization and stable scoped file-list / download endpoints; determine any developer registration.
2. Verify read-only listing/pagination and safe streamed single-image download with a consenting account **on device**, never via public logs or PR.
3. Record HTTP failure modes / token refresh behavior and service limits; no assumptions about PAT transport.
4. Wire official adapter + UI only after (1–3); do not ship speculative password collection or hacked login.
5. Build on CI, then verify ColorOS16 lockscreen behavior on real device.
