# Paperize architecture analysis (upstream `master`, commit `a84bfab`)

## Verified source overview
- Build: `app/build.gradle.kts` targets minSdk 31 / compileSdk 37 / targetSdk 36; Java 17, Compose, Hilt, Room, WorkManager, Coil 3. Version 4.2.0. Dependencies in `gradle/libs.versions.toml`.
- License: GPL-3.0, original `LICENSE` remains unmodified. Modifications to redistributed derivatives must respect GPL obligations.
- The existing `.github/workflows/android-release.yml` tests on PRs to `master`, builds **signed release** only on pushes to `master` (requires signing secrets). Cloud feature needs a separate, unsigned debug workflow.
- Local source: `app/src/main/java/com/anthonyla/paperize/domain/source/DocumentSource.kt` and `app/src/main/java/com/anthonyla/paperize/data/source/AndroidDocumentSource.kt` use Android SAF `DocumentFile` / persisted read grants. `app/src/main/java/com/anthonyla/paperize/domain/usecase/ImportWallpapersUseCase.kt` maps images/folders into domain objects.
- Album persistence: `app/src/main/java/com/anthonyla/paperize/data/repository/AlbumRepositoryImpl.kt` adds entries in 500-row chunks within a Room transaction, de-duplicates by URI; `app/src/main/java/com/anthonyla/paperize/data/database/PaperizeDatabase.kt`, `entities/{AlbumEntity,FolderEntity,WallpaperEntity}.kt` and `dao/{AlbumDao,WallpaperDao}.kt`.
- **Important:** `app/src/main/java/com/anthonyla/paperize/core/WallpaperSourceType.kt` only distinguishes `DIRECT` and `FOLDER`, **not** local vs remote. Do not repurpose it as a cloud provider discriminator.
- Existing album details: `app/src/main/java/com/anthonyla/paperize/domain/model/Album.kt` and `app/src/main/java/com/anthonyla/paperize/data/database/relations/AlbumWithDetails.kt` materialize wallpaper lists; avoid loading 50k remote images into screen state.
- Shuffle: `app/src/main/java/com/anthonyla/paperize/data/repository/WallpaperRepositoryImpl.kt` calls `getWallpaperIdsByAlbum(...).shuffled()` then constructs queue rows, `app/src/main/java/com/anthonyla/paperize/data/database/dao/WallpaperQueueDao.kt`. It currently loads all IDs for the album into memory, and can rebuild queues for both screens.
- Change lifecycle: `app/src/main/java/com/anthonyla/paperize/domain/usecase/ChangeWallpaperUseCase.kt` prepares a bitmap, restores a dequeued item on failed/cancelled application, commits current state only **after** success.
- Decoding/effects: `app/src/main/java/com/anthonyla/paperize/core/util/WallpaperRenderer.kt` converts a wallpaper URI and calls `retrieveBitmap` in `app/src/main/java/com/anthonyla/paperize/core/util/WallpaperUtil.kt`. A remote `https://` URI cannot simply be placed into the existing ContentResolver path.
- Screens: `app/src/main/java/com/anthonyla/paperize/service/wallpaper/WallpaperController.kt` supports `HOME`, `LOCK`, `BOTH`; a shared album and non-separated schedule can apply one image to both via `WallpaperManager.FLAG_SYSTEM | FLAG_LOCK`. System acceptance is still vendor-dependent.
- Scheduling: `app/src/main/java/com/anthonyla/paperize/service/worker/WallpaperScheduler.kt` uses WorkManager periodic work (>=15 minutes), `WallpaperChangeWorker.kt` executes; `AlbumRefreshWorker.kt` rescans **local** folders only. Do not use that pruning routine for remote IDs.
- User settings: `app/src/main/java/com/anthonyla/paperize/data/datastore/PreferencesManager.kt` and `app/src/main/java/com/anthonyla/paperize/domain/model/ScheduleSettings.kt` track per-screen settings, shuffle, interval, selections.
- Tests: `app/src/test/` and `app/src/androidTest/` include queues, use cases, scheduling, migration and real Android rendering tests.

## Integration seam
Keep original source, album and wallpaper tables untouched initially. Add an isolated cloud metadata database, a provider-neutral paginated `CloudFileGateway`, a streaming metadata scanner, and private cache. Later add a resolver before bitmap decoding: cloud file ID -> verified authorized download -> private `file://` / internal `content://` local cache -> existing renderer. Local SAF flow stays unchanged. Wire after official PikPak authorization and read-only listing/download endpoints are verified.

### Reusable
- `WallpaperController` screen flags and synchronized semantics;
- `ChangeWallpaperUseCase` prepare/apply/commit/rollback contract;
- bitmap scaling/effects in `WallpaperRenderer`;
- settings/scheduling infrastructure (cloud sync gets its own network constraints).

### Required later refactors
- Source-specific resolver before URI decode, with failure/cancellation behavior;
- pageable gallery / thumbnail UI that does not materialize all cloud file objects;
- scalable shuffle: DB-seeded batches, recent-history exclusion, safe queue refill;
- cloud index transactions and generation-based stale pruning **only after fully successful sync**;
- foreground UI for login, folder tree, cache/network/privacy controls.

## Known platform caveats
On ColorOS 16, background scheduling can be delayed or killed by battery policy; WorkManager does not promise exact-minute triggers. Static HOME/LOCK requests must be tested on an Ace 2; do not bypass OEM security controls.

## Verified vs not verified
**Verified:** paths, code behavior and build configuration above from GitHub source inspection.
**Not verified:** PikPak personal account authorization, API responses, download URLs, app behavior on OnePlus Ace 2, CI build success.
