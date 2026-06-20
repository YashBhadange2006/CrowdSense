# CrowdSense / YATRI — Feature track

## AI execution rule

Always:

1. Read **Tasks.md** (this file) first  
2. Pick the next task from **In progress** (or the first unchecked **Future** item if nothing is in progress)  
3. Complete **only** that task unless a tiny dependency fix is unavoidable  
4. Update this file after completion: move items, check `[x]`, refresh **Status summary** and **Relevant files**

---

## Status summary

| | Count |
|--|------|
| Total tracked tasks | See Future / Completed below |
| Completed | Station list + geohash model, per-geohash Firebase, Search badges + nav, station Insights |
| In progress | — |
| Next task | Optional: Insights tab empty-state copy; DB rules; pagination / time-window fetch |

---

## Completed tasks

- [x] **`Station`** model with **`id`**, **`name`**, **`lat`**, **`lng`**, **`geohash`** (precision **6**, matches **`FirebaseUploader`**)  
- [x] Load stations from bundled **`assets/export.geojson`** (OSM FeatureCollection) via **`StationCatalog`** — filters **`railway` ∈ { station, halt }**, skips unnamed / invalid points  
- [x] **`CrowdDataRepository.fetchReadingsForGeohash`** — reads **`/readings/{geohash}`**, cap **500** for charts  
- [x] **Search**: list from catalog; **per-station badge** from **`RemoteCrowdPoint`** exact **`geohash`** match (**NO DATA** if none); **tap → `insights/station/{geohash}`**  
- [x] **Insights**: **`stationGeohash`** arg loads **station path** + regression; **Insights tab** still uses **GPS + `fetchNearbyReadings`** when **`stationGeohash == null`**  
- [x] **Navigation**: route **`insights/station/{geohash}`**; bottom bar highlights **Insights** for **`insights/***`  

---

## In progress

- [ ] *(empty)*

---

## Future tasks

### A. Stations & data model

- [x] Add **`lat` / `lng`** (and derived **`geohash`**) — **done** via GeoJSON + **`StationCatalog`**  
- [x] Centralize station list — **`StationCatalog`** + **`assets/export.geojson`**  
- [ ] Optional: **line / platform / custom attributes** — deferred (you will add later)  

### B. Firebase: per-station reads (“unlocks”)

- [x] Repository read **only** **`/readings/{geohash}`** for station Insights — **`fetchReadingsForGeohash`**  
- [ ] Review **Realtime Database rules** for read paths (still manual in console)  

### C. Search screen — badges & navigation

- [x] Per-station badges from **`FirebaseReader`** + exact geohash match  
- [x] Card tap → station Insights  

### D. Insights screen — station-scoped, predictions in one place

- [x] From Search: data by **station geohash**, not GPS  
- [x] Predictions stay on **Insights** from that station’s history  
- [ ] Optional: **“Nearest station: …”** inside Insights (GPS vs catalog)  
- [ ] Optional: **Insights tab** empty state (“pick a station in Search”) instead of immediate GPS — **not implemented**; tab still uses **last GPS + 100m**  

### E. Charts & “full data” fetch

- [x] Station path: **up to 500** readings (was **50** global nearby)  
- [ ] Pagination / **time-window** query if Firebase rules/structure allow  

### F. Navigation (Compose)

- [x] **`insights/station/{geohash}`** + Search wiring  

---

## Implementation plan (how we tackle it)

1. **Model first** — **`stations/Station.kt`** + **`StationCatalog`** from **`assets/export.geojson`**.  
2. **Firebase reads by geohash** — **`fetchReadingsForGeohash`**.  
3. **Search UI** — **`remotePoints`** + exact **`geohash`** badge.  
4. **Navigation** — **`MainActivity`** NavHost + **`SearchScreen(onStationClick)`**.  
5. **Insights** — **`InsightsScreen(..., stationGeohash)`** branches fetch.  
6. **Charts / limits** — station uses **500** cap; GPS tab still **50** + full-tree **`get()`** (improve later).  
7. **Tasks.md** — update after each milestone.

---

## Acceptance criteria (high level)

- **Search**: Badge from **Firebase `latest`** for that **geohash**, or **NO DATA**.  
- **Search → Insights**: Charts + prediction for **that geohash** only.  
- **Insights tab**: Still **GPS 100m** until we add an empty-state / last-station policy.  
- **Firebase**: Station Insights **does not** download all **`/readings`** — only one geohash child.

---

## Dependencies

- Station Insights depends on **`fetchReadingsForGeohash`** (done).  
- Accurate badges depend on **same geohash precision** as uploads (**6**). If phone and OSM point differ, **exact match** may miss — see **Notes**.

---

## Relevant files (update as we go)

| File | Role |
|------|------|
| `app/src/main/assets/export.geojson` | Bundled OSM stations (copy of `res/raw/export.geojson`) |
| `app/src/main/java/com/example/ble/stations/Station.kt` | Minimal station model |
| `app/src/main/java/com/example/ble/stations/StationCatalog.kt` | Parse GeoJSON, cache list, **`findByGeohash`** |
| `app/src/main/java/com/example/ble/userinterface/screen/SearchScreen.kt` | Search + cards + **`NO DATA`** badge |
| `app/src/main/java/com/example/ble/userinterface/screen/InsightsScreen.kt` | **`stationGeohash`** + GPS modes |
| `app/src/main/java/com/example/ble/CrowdDataRepository.kt` | **`fetchReadingsForGeohash`**, charts, regression |
| `app/src/main/java/com/example/ble/FirebaseReader.kt` | **`/latest`** for badges |
| `app/src/main/java/com/example/ble/MainActivity.kt` | Nav routes, **`remotePoints`**, **`stations`**, IO load |
| `app/build.gradle.kts` | **`kotlinx-coroutines-android`** (IO parse) |

---

## Notes

- **Insights charts** (Compose-only, no extra chart lib): scrollable bucket chart with Y-axis, full **date/time window** header, per-bucket **tap detail** (`bucketDescription` from `CrowdDataRepository`). Optional later: swap to [Vico](https://github.com/patrykandpatrick/vico), [Composable-Graphs](https://github.com/jaikeerthick/Composable-Graphs), or [ComposeCharts](https://github.com/ehsannarmani/ComposeCharts) if you want built-in axes/animations.
- **Geohash mismatch**: Uploads use **device GPS**; stations use **OSM coordinates**. Rarely, **precision-6** cells may differ → badge **NO DATA** though crowd exists nearby — mitigations later (parent geohash, distance).  
- **OSM attribution**: Data derived from **OpenStreetMap** — add **About** credit when you polish UI.  
- **No separate “nearest station prediction” screen** — optional line inside Insights only.  
- **GSIM / BtGatt `E` logs** on Samsung: often normal stack noise.

---

## Legacy: generic task-list template

<details>
<summary>Generic template (reference only)</summary>

- Use checkboxes `[ ]` / `[x]`, move items between **Completed** / **In progress** / **Future**  
- Keep **Relevant files** and **Implementation plan** updated after meaningful changes  

</details>
