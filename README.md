# riftmana-tracker

Logs the "Total Value" from https://riftmana.com/collection/?user=moose
once an hour and appends it to [total_value_history.csv](total_value_history.csv).

## How it works

- [track_riftmana.py](track_riftmana.py) uses Playwright (headless Chromium)
  to load the page, because the site sits behind a Cloudflare bot-challenge
  that blocks plain HTTP requests (`curl`, `requests`, Apps Script's
  `UrlFetchApp`, etc. all get a 403 "Just a moment..." page). It retries a
  few times with a fresh browser context if the challenge doesn't clear
  immediately.
- [.github/workflows/track.yml](.github/workflows/track.yml) runs that
  script every hour on GitHub's free Actions runners and commits the updated
  CSV back to this repo. No computer of yours needs to be on.
- [plot_history.py](plot_history.py) is an optional local helper to chart
  `total_value_history.csv` (`pip install matplotlib pandas`, then
  `python plot_history.py`).

## One-time setup

1. Push this repo to GitHub (public or private both work; private repos on
   the free plan get 2,000 Actions minutes/month, which is far more than an
   hourly job needs).
2. That's it — the workflow starts running hourly as soon as it's on the
   default branch. Trigger a run manually anytime from the repo's
   **Actions** tab ("Track riftmana.com Total Value" -> "Run workflow").

## Notes

- If a given hourly run can't get past Cloudflare after its retries, it
  fails without committing a row for that hour rather than logging a bad
  value — gaps in the CSV mean a run failed, not that the value didn't
  change.
- The regex used to find the value (`VALUE_RE` in track_riftmana.py) looks
  for "Total Value" followed by a dollar amount anywhere in the rendered
  page text, so it isn't tied to exact CSS class names and should survive
  minor styling changes to the site.
