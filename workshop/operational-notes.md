# Workshop operational notes

Risks the speaker should plan for, with concrete mitigations and fallback paths. Read this once during prep; refer to it on the day if something misbehaves.

## Risk matrix

| Risk | Likelihood | Impact | Mitigation | Fallback |
|---|---|---|---|---|
| Attendee on macOS hits `find -printf` errors | Low | Workflow break | `record-scenarios.sh` uses `ls -1t`, portable across BSD/GNU find | The pre-recorded `.jfr` files in `workshop/recordings/` cover the same scenarios |
| Azul Platform Prime image fails to pull (no internet at venue) | Medium | Module 4 dies | `docker save trader-stream-ee:workshop > workshop-image.tar` ahead of time; ship on USB | Switch the workshop compose to `Dockerfile.scale.standard` (Temurin) and lose only the C4 narrative for that session |
| Zing fatal: "Checkpoint sync time longer than 200000 ms" under sustained scenario load | Medium on 8C/16T laptops | Container restart mid-scenario | `docker-compose-workshop.yml` runs ONE instance instead of three, which removes the per-thread Aeron busy-spin contention. Do not use `start-comparison.sh` (3 + 3) on laptops | Restart the container, rerun the single failing scenario |
| JDK Mission Control not installed on attendee laptop | Medium | Can't open recordings | `workshop/scripts/verify-setup.sh` checks and prints install URLs | Use the CLI helper `workshop/scripts/jfr-query.sh` to read recordings from the terminal |
| Laptop has < 8 GB RAM free | Low for senior dev audience | OOM during scenario | Workshop compose uses 2 GB heap; the host needs ~4 GB total | Reduce `-Xms`/`-Xmx` to 1g in `docker-compose-workshop.yml` |
| Conference Wi-Fi blocks Docker Hub or nexus.payara.fish | Medium | First-run build fails | `docker compose -f docker-compose-workshop.yml build` before leaving home; the image is now cached locally | USB stick with `workshop-image.tar` + `docker load -i workshop-image.tar` |
| HTTP request to the JFR API hangs > 15 s under load | Medium | Recording script aborts | `record-scenarios.sh` already uses `curl --max-time 15 --retry 3 --retry-all-errors` | Use the dump-on-exit pattern: stop the container, the always-on recording flushes to disk via `dumponexit=true` |
| Attendee runs `mvn spotless:apply` and breaks Marp YAML | Low | Slides won't render | `pom.xml` excludes `workshop/slides/**` and `workshop/WORKSHOP.md` from the spotless markdown formatter | `git checkout workshop/slides/` to revert |
| Hazelcast cluster discovery noise floods the logs | Low | Hard to read live logs | Workshop compose runs a single node, so the cluster is size 1 and quiet | n/a |
| Attendee falls behind in a hands-on segment | Medium | Frustration | Each exercise README has hint progressions and an "expected output" file; pre-recorded files mean every exercise is achievable even if their cluster isn't producing | Pair them with an attendee who is on track |
| Zing checkpoint abort recurs on the single workshop instance | Low | Module 3 / 5 disruption | Single instance means no inter-MediaDriver contention. If it still happens, the host is genuinely overloaded | Switch to Temurin via `Dockerfile.scale.standard` for that session |

## What attendees actually need (minimum)

- Docker 24+ with 4 GB allocated to the engine
- 10 GB free disk
- JDK 21+ on `PATH` (for the `jfr` CLI used by the workshop scripts)
- JDK Mission Control (free downloads listed in `workshop/README.md`)
- A browser
- These ports free: 8080 for workshop compose; 8080–8084 and 9080–9084 only if running the full comparison stack

## Hard-earned diagnostic checklist

When something doesn't work mid-workshop, run these in order:

```bash
# 1. Is the container alive?
docker ps --filter name=trader-stream-workshop

# 2. Is the app responding?
curl -fsS http://localhost:8080/trader-stream-ee/api/health/check | jq .status

# 3. Did JFR initialise?
curl -fsS http://localhost:8080/trader-stream-ee/api/jfr/status | jq

# 4. What does the app see?
docker logs --tail 30 trader-stream-workshop

# 5. Is the JVM healthy?
docker exec trader-stream-workshop sh -c 'ls /opt/payara/hs_err_pid* 2>/dev/null && cat /opt/payara/hs_err_pid*.log | head -40'

# 6. Disk full? Bind mount writable?
docker exec trader-stream-workshop sh -c 'touch /opt/payara/recordings/.write-test && rm /opt/payara/recordings/.write-test && echo OK'
```

If steps 1–4 are healthy but a scenario still misbehaves, the issue is almost always (a) the wrong REST path (it is `/api/pressure/mode/...`, not `/api/memory/mode/...`) or (b) the scenario is still running from a previous attempt. Run `curl -X POST .../api/pressure/mode/OFF` and try again.
