"""
Tracks the "Total Value" shown on https://riftmana.com/collection/?user=moose
over time by appending a timestamped row to a CSV file on every run.

The site sits behind Cloudflare's bot-challenge ("Just a moment...") so plain
HTTP requests (requests/curl) get a 403 and never see the real page - a real
browser has to render it. This script uses Playwright (headless Chromium) to
render the page. The challenge doesn't always clear on the first try, so it
retries a few times with a fresh browser context before giving up.

Setup (one-time):
    pip install -r requirements.txt
    python -m playwright install --with-deps chromium

Run:
    python track_riftmana.py

Intended to run hourly via the GitHub Actions workflow in
.github/workflows/track.yml (see that file / README for the scheduled setup).
"""

import argparse
import csv
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

URL = "https://riftmana.com/collection/?user=moose"
SCRIPT_DIR = Path(__file__).resolve().parent
CSV_PATH = SCRIPT_DIR / "total_value_history.csv"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

# Matches "Total Value" followed (within a short distance) by a dollar amount,
# e.g. "Total Value $12,345.67" or "Total Value\n$12,345.67".
VALUE_RE = re.compile(
    r"Total\s*Value\D{0,20}?(\$?\s?[\d,]+(?:\.\d+)?)",
    re.IGNORECASE,
)


def try_extract_total_value(browser) -> str | None:
    """One attempt: open a fresh context/page, load the URL, look for Total Value.
    Returns the matched text, or None if this attempt didn't find it."""
    context = browser.new_context(
        viewport={"width": 1280, "height": 900},
        user_agent=USER_AGENT,
    )
    page = context.new_page()
    try:
        page.goto(URL, wait_until="domcontentloaded", timeout=45_000)
        page.wait_for_load_state("load", timeout=30_000)
        # This SPA keeps background requests (polling/analytics) alive
        # indefinitely, so "networkidle" never fires. Instead, poll the
        # rendered DOM text directly until the "Total Value ..." pattern
        # shows up, or the Cloudflare challenge visibly clears.
        try:
            page.wait_for_function(
                r"""() => /Total\s*Value\D{0,20}?\$?\s?[\d,]+(?:\.\d+)?/i.test(document.body.innerText)""",
                timeout=20_000,
            )
        except PlaywrightTimeoutError:
            return None

        # The page briefly shows a "$0.00" skeleton/placeholder before the
        # real figure loads in. $0.00 is never a real reading (a collection
        # with zero cards wouldn't have a page), so it's excluded outright;
        # beyond that, don't trust the first match at all - poll until two
        # consecutive reads agree (the number has settled), since a slower
        # runner can take several seconds longer than a local machine to
        # replace the placeholder.
        previous = None
        for _ in range(12):
            body_text = page.inner_text("body")
            match = VALUE_RE.search(body_text)
            current = match.group(1).strip() if match else None
            if current is not None and to_numeric(current) == 0:
                current = None  # placeholder; treat as "not loaded yet"
            if current is not None and current == previous:
                return current
            previous = current
            page.wait_for_timeout(2_500)
        return previous
    except PlaywrightTimeoutError:
        return None
    finally:
        context.close()


def extract_total_value(browser, attempts: int = 3, delay_seconds: int = 10) -> str:
    for attempt in range(1, attempts + 1):
        value = try_extract_total_value(browser)
        if value:
            return value
        if attempt < attempts:
            print(
                f"Attempt {attempt}/{attempts}: 'Total Value' not found yet "
                f"(likely still on Cloudflare's challenge page). Retrying...",
                file=sys.stderr,
            )
            time.sleep(delay_seconds)
    raise RuntimeError(
        f"Could not find 'Total Value' after {attempts} attempts. The site "
        "layout may have changed, or Cloudflare blocked every attempt this run."
    )


def append_to_csv(timestamp: str, raw_value: str, numeric_value: float | None) -> None:
    is_new_file = not CSV_PATH.exists()
    with CSV_PATH.open("a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        if is_new_file:
            writer.writerow(["timestamp_utc", "total_value_raw", "total_value_numeric"])
        writer.writerow([timestamp, raw_value, numeric_value])


def to_numeric(raw_value: str) -> float | None:
    cleaned = raw_value.replace("$", "").replace(",", "").strip()
    try:
        return float(cleaned)
    except ValueError:
        return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--attempts",
        type=int,
        default=3,
        help="How many times to retry loading the page if the Cloudflare "
        "challenge doesn't clear (default: 3).",
    )
    args = parser.parse_args()

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            raw_value = extract_total_value(browser, attempts=args.attempts)
        except RuntimeError as e:
            print(f"ERROR: {e}", file=sys.stderr)
            browser.close()
            return 1
        browser.close()

    timestamp = datetime.now(timezone.utc).isoformat(timespec="seconds")
    numeric_value = to_numeric(raw_value)
    append_to_csv(timestamp, raw_value, numeric_value)
    print(f"[{timestamp}] Total Value = {raw_value} -> logged to {CSV_PATH.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
