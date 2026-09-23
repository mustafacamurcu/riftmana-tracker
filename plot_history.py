"""
Plots total_value_history.csv (produced by track_riftmana.py) over time.

    pip install matplotlib pandas
    python plot_history.py
"""

from pathlib import Path

import pandas as pd
import matplotlib.pyplot as plt

CSV_PATH = Path(__file__).resolve().parent / "total_value_history.csv"


def main() -> None:
    df = pd.read_csv(CSV_PATH, parse_dates=["timestamp_utc"])
    df = df.dropna(subset=["total_value_numeric"])

    fig, ax = plt.subplots(figsize=(10, 5))
    ax.plot(df["timestamp_utc"], df["total_value_numeric"], marker="o", linewidth=1)
    ax.set_title("riftmana.com Collection Total Value Over Time (moose)")
    ax.set_xlabel("Time (UTC)")
    ax.set_ylabel("Total Value")
    ax.grid(True, alpha=0.3)
    fig.autofmt_xdate()
    fig.tight_layout()

    out_path = CSV_PATH.parent / "total_value_history.png"
    fig.savefig(out_path, dpi=150)
    print(f"Saved chart to {out_path}")
    plt.show()


if __name__ == "__main__":
    main()
