"""
Scrapes per-card prices for every card in moose's RiftMana collection and
appends an hourly snapshot to card_price_history.csv, then computes the
biggest 24h movers into movers.json.

The full card list is normally loaded by an in-page AJAX call to
wp-admin/admin-ajax.php (action=shared_collection_load_cards) after you click
a set. That endpoint gets Cloudflare-challenge-blocked almost every time it's
*not* the first admin-ajax request of a fresh browser session - but loading
the page directly at .../collection/?user=moose&set=all makes it the first
(and only) such request, which reliably gets through. See README for more.

Only OWNED cards are stored - the point is tracking value movement within
the collection you actually have, not the full ~1400-card catalog.

Setup: same as track_riftmana.py (same Playwright install), plus:
    pip install -r requirements.txt   # now also installs beautifulsoup4/lxml

Run:
    python track_card_prices.py
"""

import csv
import json
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright, TimeoutError as PlaywrightTimeoutError

URL = "https://riftmana.com/collection/?user=moose&set=all"
SCRIPT_DIR = Path(__file__).resolve().parent
HISTORY_PATH = SCRIPT_DIR / "card_price_history.csv"
MOVERS_PATH = SCRIPT_DIR / "movers.json"
USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)

FIELDNAMES = [
    "timestamp_utc", "card_id", "card_name", "set", "set_slug", "rarity",
    "normal_owned", "foil_owned", "normal_price", "foil_price", "value",
]

MOVERS_WINDOW_HOURS = 24
MOVERS_WINDOW_TOLERANCE_HOURS = 4  # accept a baseline reading this far off the exact 24h mark
TOP_N = 10


def try_fetch_cards_html(browser) -> str | None:
    """One attempt: fresh context (fresh session), load the URL, capture the
    load_cards AJAX response body. Returns the HTML fragment, or None."""
    context = browser.new_context(
        viewport={"width": 1280, "height": 900},
        user_agent=USER_AGENT,
    )
    page = context.new_page()
    captured: dict[str, str] = {}

    def on_response(response):
        if "admin-ajax" in response.url:
            post_data = response.request.post_data or ""
            if "load_cards" in post_data and response.status == 200:
                try:
                    captured["html"] = response.text()
                except Exception:
                    pass

    page.on("response", on_response)
    try:
        page.goto(URL, wait_until="domcontentloaded", timeout=45_000)
        page.wait_for_timeout(15_000)
    except PlaywrightTimeoutError:
        pass
    finally:
        context.close()

    if "html" not in captured:
        return None
    try:
        payload = json.loads(captured["html"])
        return payload["data"]["html"]
    except Exception:
        return None


def parse_owned_cards(html_fragment: str) -> list[dict]:
    soup = BeautifulSoup(html_fragment, "lxml")
    rows = []
    for el in soup.select(".shared-collection-card.is-owned"):
        info = {}
        info_el = el.select_one("[data-card-info]")
        if info_el and info_el.get("data-card-info"):
            try:
                info = json.loads(info_el["data-card-info"])
            except Exception:
                pass

        a = el.attrs
        try:
            normal_owned = int(a.get("data-normal-owned") or 0)
            foil_owned = int(a.get("data-foil-owned") or 0)
            normal_price = float(a.get("data-normal-price") or 0)
            foil_price = float(a.get("data-foil-price") or 0)
        except ValueError:
            continue

        rows.append({
            "card_id": a.get("data-card-id", ""),
            "card_name": info.get("cardName", ""),
            "set": info.get("set", ""),
            "set_slug": info.get("setSlug", ""),
            "rarity": a.get("data-rarity", ""),
            "normal_owned": normal_owned,
            "foil_owned": foil_owned,
            "normal_price": normal_price,
            "foil_price": foil_price,
            "value": round(normal_owned * normal_price + foil_owned * foil_price, 4),
        })
    return rows


def extract_owned_cards(attempts: int = 3, delay_seconds: int = 10) -> list[dict]:
    with sync_playwright() as p:
        browser = p.chromium.launch(
            headless=True,
            args=["--disable-blink-features=AutomationControlled"],
        )
        try:
            for attempt in range(1, attempts + 1):
                html = try_fetch_cards_html(browser)
                if html:
                    rows = parse_owned_cards(html)
                    if rows:
                        return rows
                if attempt < attempts:
                    print(
                        f"Attempt {attempt}/{attempts}: card data not captured yet "
                        "(likely Cloudflare-challenged). Retrying with a fresh session...",
                        file=sys.stderr,
                    )
                    time.sleep(delay_seconds)
        finally:
            browser.close()
    return []


def load_history() -> list[dict]:
    if not HISTORY_PATH.exists():
        return []
    with HISTORY_PATH.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def append_history(rows: list[dict], timestamp: str) -> None:
    is_new = not HISTORY_PATH.exists()
    with HISTORY_PATH.open("a", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDNAMES)
        if is_new:
            writer.writeheader()
        for r in rows:
            writer.writerow({"timestamp_utc": timestamp, **r})


def compute_movers(current_rows: list[dict], history_rows: list[dict], now: datetime) -> dict:
    target = now - timedelta(hours=MOVERS_WINDOW_HOURS)
    tolerance = timedelta(hours=MOVERS_WINDOW_TOLERANCE_HOURS)

    # For each card_id, find the historical snapshot closest to `target`.
    best_by_card: dict[str, tuple[timedelta, dict]] = {}
    for row in history_rows:
        try:
            ts = datetime.fromisoformat(row["timestamp_utc"])
        except ValueError:
            continue
        diff = abs(ts - target)
        if diff > tolerance:
            continue
        card_id = row["card_id"]
        if card_id not in best_by_card or diff < best_by_card[card_id][0]:
            best_by_card[card_id] = (diff, row)

    movers = []
    for row in current_rows:
        baseline_entry = best_by_card.get(row["card_id"])
        if not baseline_entry:
            continue
        baseline = baseline_entry[1]

        # If you bought or sold copies since the baseline snapshot, the value
        # delta reflects that collection change, not a market price move -
        # showing it as a "mover" would be misleading, so skip it. (A card
        # that's fully sold off just disappears from current_rows on its own
        # since only owned cards are scraped; a newly-acquired card has no
        # baseline at all and is already skipped above.)
        if (
            int(baseline["normal_owned"]) != row["normal_owned"]
            or int(baseline["foil_owned"]) != row["foil_owned"]
        ):
            continue

        value_prior = float(baseline["value"])
        value_now = row["value"]
        if value_prior == 0 and value_now == 0:
            continue
        delta = round(value_now - value_prior, 4)
        if abs(delta) < 0.01:
            continue
        delta_pct = round((delta / value_prior) * 100, 2) if value_prior > 0 else None
        movers.append({
            "card_id": row["card_id"],
            "card_name": row["card_name"],
            "set": row["set"],
            "rarity": row["rarity"],
            "normal_owned": row["normal_owned"],
            "foil_owned": row["foil_owned"],
            "normal_price_now": row["normal_price"],
            "normal_price_prior": float(baseline["normal_price"]),
            "foil_price_now": row["foil_price"],
            "foil_price_prior": float(baseline["foil_price"]),
            "value_now": value_now,
            "value_prior": value_prior,
            "value_delta": delta,
            "value_delta_pct": delta_pct,
        })

    gainers = sorted([m for m in movers if m["value_delta"] > 0], key=lambda m: -m["value_delta"])[:TOP_N]
    losers = sorted([m for m in movers if m["value_delta"] < 0], key=lambda m: m["value_delta"])[:TOP_N]

    return {
        "computed_at": now.isoformat(timespec="seconds"),
        "window_hours": MOVERS_WINDOW_HOURS,
        "baseline_cards_matched": len(best_by_card),
        "gainers": gainers,
        "losers": losers,
    }


def main() -> int:
    now = datetime.now(timezone.utc)
    timestamp = now.isoformat(timespec="seconds")

    rows = extract_owned_cards()
    if not rows:
        print("ERROR: Could not extract owned-card data after retries.", file=sys.stderr)
        return 1

    history_rows = load_history()
    movers = compute_movers(rows, history_rows, now)

    append_history(rows, timestamp)
    MOVERS_PATH.write_text(json.dumps(movers, indent=2) + "\n", encoding="utf-8")

    total_value = round(sum(r["value"] for r in rows), 2)
    print(
        f"[{timestamp}] Snapshot: {len(rows)} owned cards, ${total_value} total. "
        f"Movers: {len(movers['gainers'])} gainers / {len(movers['losers'])} losers "
        f"(baseline matched {movers['baseline_cards_matched']} cards)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
