# riftmana-tracker

Logs the "Total Value" from https://riftmana.com/collection/?user=moose
once an hour, charts it on a phone-friendly web page, and shows it on an
Android home-screen widget.

**Live chart:** https://mustafacamurcu.github.io/riftmana-tracker/

## How it works

- [track_riftmana.py](track_riftmana.py) uses Playwright (headless Chromium)
  to load the page, because the site sits behind a Cloudflare bot-challenge
  that blocks plain HTTP requests (`curl`, `requests`, Apps Script's
  `UrlFetchApp`, etc. all get a 403 "Just a moment..." page). It retries a
  few times with a fresh browser context if the challenge doesn't clear
  immediately, and writes both `total_value_history.csv` (full history) and
  `latest.json` (just the current reading, for lightweight consumers).
- **Runs locally, not on GitHub Actions.** GitHub's hosted runner IPs get
  hard-blocked by Cloudflare every time (confirmed: 10/10 attempts failed
  across two scheduled runs, while the same script passes instantly from a
  home IP). [scripts/run_and_publish.ps1](scripts/run_and_publish.ps1) runs
  the tracker and pushes the result to GitHub; a Windows Task Scheduler job
  (see [scripts/schedule_task.ps1](scripts/schedule_task.ps1)) fires it
  hourly whenever this computer is on. The `.github/workflows/track.yml`
  workflow is kept for manual/debug runs only (no schedule).
- GitHub Pages serves [index.html](index.html) — a single-file page (no
  external dependencies) that fetches `total_value_history.csv` and renders
  a hero figure + line chart + table view. Updates automatically whenever
  the local job pushes new data.
- [android-widget/](android-widget/) is a small native Android app
  (Kotlin) with a home-screen widget that polls `latest.json` on a 30-minute
  WorkManager schedule (or on tap) and shows the current value. See
  [android-widget/README.md](android-widget/README.md) to build/install it.
- [track_card_prices.py](track_card_prices.py) scrapes a price/quantity
  snapshot for every *owned* card (not the full ~1400-card catalog) each
  hour, via the same AJAX endpoint the site's own collection page uses
  internally. Appends to `card_price_history.csv` and computes the biggest
  24h value movers into `movers.json`, which the chart page renders as a
  "Today's Biggest Movers" gainers/losers section. Best-effort: if this step
  fails, the core Total Value tracking still publishes normally (see
  `run_and_publish.ps1`).
- [plot_history.py](plot_history.py) is an optional local helper to chart
  `total_value_history.csv` from the command line
  (`pip install matplotlib pandas`, then `python plot_history.py`).

## One-time setup (already done on this machine)

1. `pip install -r requirements.txt && python -m playwright install chromium`
2. `powershell -ExecutionPolicy Bypass -File scripts\schedule_task.ps1`
   registers the hourly Task Scheduler job. Run this from an **elevated**
   PowerShell so it can register under an S4U logon (fires even when logged
   out, without storing your Windows password) — an unelevated run falls
   back to "only while logged on."
3. GitHub Pages is enabled on this repo (Settings → Pages → deploys from
   `master` / `/`), and the repo is public (required for Pages on the free
   plan).

## Notes

- If a given hourly run can't get past Cloudflare after its retries, it
  fails without committing a row for that hour rather than logging a bad
  value — gaps in the CSV mean a run failed, not that the value didn't
  change.
- The site briefly shows a "$0.00" loading skeleton before the real figure
  renders; the script explicitly waits for two consecutive non-zero reads
  to agree before accepting a value, so it doesn't log the placeholder.
- The regex used to find the value (`VALUE_RE` in track_riftmana.py) looks
  for "Total Value" followed by a dollar amount anywhere in the rendered
  page text, so it isn't tied to exact CSS class names and should survive
  minor styling changes to the site.
- Logs from the scheduled task: `logs/run_and_publish.log`.
- The per-card AJAX endpoint (`shared_collection_load_cards`) only reliably
  passes Cloudflare when it's the *first* `admin-ajax.php` request of a fresh
  browser session — any follow-up AJAX call in that same session gets
  challenge-blocked almost every time, regardless of pacing. Loading the page
  at `?user=moose&set=all` (rather than clicking a set after the page's own
  `load_sets` call has already fired) makes the cards call that first
  request. `track_card_prices.py` opens a brand-new browser context per
  retry attempt specifically to get a fresh "first request" each time.
