# TradeStreamEE Workshop: Java Flight Recorder for Low-Latency Systems

Hands-on workshop materials for JNation.

## Quick map

| You want to | Read this |
|---|---|
| Run the workshop end-to-end | [WORKSHOP.md](./WORKSHOP.md) |
| Speaker day-of checklist | [speaker-prep.md](./speaker-prep.md) |
| Risk matrix and live diagnostic steps | [operational-notes.md](./operational-notes.md) |
| Verify your laptop is ready | `./scripts/verify-setup.sh` |
| Print a desk reference | [analysis-checklist.md](./analysis-checklist.md) |
| Read the speaker slides | [slides/slides.md](./slides/slides.md) |
| Reproduce the pre-recorded files | `./scripts/record-scenarios.sh` |
| Compare two recordings on the CLI | `./scripts/compare-recordings.sh` |
| Inspect any recording from the terminal | `./scripts/jfr-query.sh summary <file>.jfr` |
| Add a Module 3 starter to the source tree | `./scripts/install-exercise.sh module-3-burst-event` |
| GC pathology catalogue (take-home reference) | [exercises/module-5-apply-to-your-app/gc-pathology-catalog.md](./exercises/module-5-apply-to-your-app/gc-pathology-catalog.md) |

## JFR mode

By default the cluster does **not** run an always-on JFR recording. Every `.jfr` file is produced on demand via the REST API (`POST /api/jfr/recording/start`), which is what the workshop scripts and `WORKSHOP.md` use throughout.

To opt in to a long-running circular always-on recording in addition (for example to capture the steady-state behaviour before any scenario fires), set `JFR_ALWAYS_ON=true` in the relevant Docker Compose service environment. The container honours the flag at startup and adds an always-on recording dumping to `/opt/payara/recordings/recording.jfr` (bind-mounted to `monitoring/recordings/{cluster}-N/` on the host).

## Pre-workshop checklist

1. **Docker** with at least 8 GB RAM allocated to the engine, and 10 GB free disk.
2. **JDK 21+** on your PATH (used by the `jfr` CLI for the comparison script).
3. **JDK Mission Control** installed (`jmc` command available). Free options:
   - Azul Mission Control: <https://www.azul.com/products/components/azul-mission-control/>
   - OpenJDK Mission Control: <https://github.com/openjdk/jmc>
4. Ports `8080-8084`, `9080-9084`, `9090`, `3000`, `3100` free.
5. Run `./scripts/verify-setup.sh` and resolve any `[FAIL]` items.
6. Run `./start-comparison.sh all` once before the workshop to pre-cache Docker images.

## Folder layout

```
workshop/
├── WORKSHOP.md              # The main guide, 3.5 hours of content
├── README.md                # This file
├── analysis-checklist.md    # Printable 2-page JFR triage reference
├── jfr-settings/
│   └── tradestream-workshop.jfc
├── recordings/              # Pre-recorded .jfr files (10 total)
├── scripts/                 # Verify, record, compare, install helpers
├── exercises/
│   ├── module-1-find-longest-pause/
│   ├── module-2-diagnose-promotion-storm/
│   ├── module-3-burst-event/
│   ├── module-4-collector-comparison/
│   └── module-5-apply-to-your-app/
└── slides/
    └── slides.md            # Marp markdown deck
```

## Rendering the slides

The deck uses [Marp](https://marp.app/). Render in your editor:

- VS Code: install the "Marp for VS Code" extension and open `slides/slides.md`.
- CLI: `npx @marp-team/marp-cli@latest slides/slides.md --pdf -o slides.pdf`

