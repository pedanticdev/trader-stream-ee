# Speaker preparation checklist

Run through this list at least 48 hours before the workshop. Most items are one-time; a few should be redone the morning of.

## Two weeks before

- [ ] Confirm the workshop room has projector resolution at least 1920×1080. Marp slides assume 16:9.
- [ ] Confirm internet availability. If unreliable, plan to ship a USB stick with the Docker image (see "Day before").
- [ ] Send the `workshop/README.md` to registered attendees with prerequisites. Two days is the minimum for them to install Docker + JMC on a clean laptop.

## One week before

- [ ] Run `./workshop/scripts/verify-setup.sh` on a clean Docker install to catch any prerequisite drift.
- [ ] Boot `docker-compose-workshop.yml` from a fresh clone, generate one baseline recording end-to-end:

  ```bash
  git clone <repo-url> /tmp/workshop-dry-run
  cd /tmp/workshop-dry-run
  docker compose -f docker-compose-workshop.yml up -d --build
  # wait for healthy
  curl -X POST 'http://localhost:8080/trader-stream-ee/api/jfr/recording/start?name=dry-run&durationSeconds=60&settings=tradestream-workshop'
  sleep 65
  ls -lh monitoring/recordings/workshop/
  docker compose -f docker-compose-workshop.yml down
  ```
- [ ] Open the produced `dry-run-*.jfr` in JMC and confirm all 14 custom application events fire (`trade.published`, `quote.published`, `gc.sla.violation`, etc.).

## Day before

- [ ] Pre-pull the image so first-run on the workshop laptop is fast:

  ```bash
  docker compose -f docker-compose-workshop.yml build
  docker save trader-stream-ee:workshop > /tmp/trader-stream-ee-workshop.tar
  ```
- [ ] Copy the tar to a USB stick as a no-internet fallback. To restore: `docker load -i /tmp/trader-stream-ee-workshop.tar`.
- [ ] Verify the pre-recorded files exist and open in JMC:

  ```bash
  ls -lh workshop/recordings/*.jfr
  jmc -open workshop/recordings/zgc-baseline.jfr
  ```
- [ ] Print one copy each per attendee:
  - `workshop/analysis-checklist.md` (2-page reference)
  - `workshop/exercises/module-4-collector-comparison/comparison-template.md` (worksheet)
- [ ] Render the slides to PDF as a backup for the projector:

  ```bash
  npx @marp-team/marp-cli@latest workshop/slides/slides.md --pdf -o /tmp/slides.pdf
  ```
- [ ] Make sure JMC starts cleanly on the projector machine (some macOS versions hide the JFR plug-in on first launch).
- [ ] Charge the workshop laptop to full and bring a power adapter that matches the venue.

## Morning of

- [ ] Reboot the workshop laptop. Aeron's embedded MediaDriver leaves shared-memory state around; a fresh boot is the cheapest insurance.
- [ ] Run `./workshop/scripts/verify-setup.sh` one final time on the workshop laptop.
- [ ] Boot the workshop stack and confirm the dashboard loads:

  ```bash
  docker compose -f docker-compose-workshop.yml up -d
  # wait ~60 seconds
  curl -fsS http://localhost:8080/trader-stream-ee/api/health/check | jq
  open http://localhost:8080/trader-stream-ee/    # macOS; xdg-open elsewhere
  ```
- [ ] Open JMC and load `workshop/recordings/zgc-baseline.jfr` so the first switch is instant.
- [ ] Keep this terminal layout open:
  - Terminal 1: Docker logs (`docker compose -f docker-compose-workshop.yml logs -f trader-stream-workshop`)
  - Terminal 2: `curl` for REST calls
  - Terminal 3: `jmc` for analysis

## During the workshop

- [ ] When demonstrating a recording, name it after the module so attendees can follow along (`?name=module-2-promotion-storm`).
- [ ] If a scenario hangs, follow the diagnostic checklist in `workshop/operational-notes.md` rather than ad-hoc debugging in front of the room.
- [ ] After each module, recap the two or three findings on the projector so attendees can take notes.

## After the workshop

- [ ] Push `jnation-workshop` to GitHub so attendees can fork the exact repo state they used.
- [ ] Tag the commit (`git tag -a workshop-jnation-2025 -m '...'`) so the working state is preserved even if `develop` moves.
- [ ] Collect feedback and file any code issues in the repo's issue tracker.

