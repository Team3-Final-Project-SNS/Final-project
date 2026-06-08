from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


OUTPUT_DIR = Path("/Users/t2025-m0040/Final-project/outputs/caching-before-after")
TESTS = ["Smoke", "Load", "Stress", "Spike"]
BEFORE_COLOR = "#94A3B8"
AFTER_COLOR = "#2563EB"
TEXT_COLOR = "#172033"

CHARTS = [
    {
        "filename": "before-after-p95.png",
        "title": "Before/After p(95)",
        "ylabel": "Latency (ms)",
        "before": [41.43, 75.17, 1410, 2710],
        "after": [38.14, 84, 277.41, 1950],
        "lower_is_better": True,
    },
    {
        "filename": "before-after-p99.png",
        "title": "Before/After p(99)",
        "ylabel": "Latency (ms)",
        "before": [51.44, 253.37, 3410, 3600],
        "after": [48.48, 296.4, 1110, 3450],
        "lower_is_better": True,
    },
    {
        "filename": "before-after-rps.png",
        "title": "Before/After RPS",
        "ylabel": "Requests per second",
        "before": [9.03, 407.98, 566.03, 798.07],
        "after": [9.06, 407.46, 710.68, 1345.79],
        "lower_is_better": False,
    },
]


def label_bars(axis, bars, values, max_value):
    for bar, value in zip(bars, values):
        offset = max_value * 0.018
        axis.text(
            bar.get_x() + bar.get_width() / 2,
            bar.get_height() + offset,
            f"{value:,.2f}",
            ha="center",
            va="bottom",
            fontsize=10,
            color=TEXT_COLOR,
            fontweight="semibold",
        )


OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
plt.rcParams.update(
    {
        "font.family": "DejaVu Sans",
        "axes.titleweight": "bold",
        "axes.labelcolor": TEXT_COLOR,
        "xtick.color": "#475569",
        "ytick.color": "#475569",
    }
)

for chart in CHARTS:
    before = np.array(chart["before"])
    after = np.array(chart["after"])
    x = np.arange(len(TESTS))
    width = 0.34
    max_value = max(before.max(), after.max())

    fig, axis = plt.subplots(figsize=(12, 6.75), dpi=160)
    fig.patch.set_facecolor("white")
    axis.set_facecolor("white")

    before_bars = axis.bar(
        x - width / 2,
        before,
        width,
        label="Before",
        color=BEFORE_COLOR,
        edgecolor="none",
        zorder=3,
    )
    after_bars = axis.bar(
        x + width / 2,
        after,
        width,
        label="After",
        color=AFTER_COLOR,
        edgecolor="none",
        zorder=3,
    )

    label_bars(axis, before_bars, before, max_value)
    label_bars(axis, after_bars, after, max_value)

    axis.set_title(chart["title"], fontsize=24, color=TEXT_COLOR, pad=22)
    axis.set_ylabel(chart["ylabel"], fontsize=13, fontweight="semibold")
    axis.set_xticks(x, TESTS, fontsize=12, fontweight="semibold")
    axis.set_ylim(0, max_value * 1.18)
    axis.grid(axis="y", color="#CBD5E1", linestyle="--", linewidth=0.8, alpha=0.8, zorder=0)
    axis.spines["top"].set_visible(False)
    axis.spines["right"].set_visible(False)
    axis.spines["left"].set_color("#CBD5E1")
    axis.spines["bottom"].set_color("#CBD5E1")
    axis.tick_params(axis="both", length=0, pad=8)
    axis.legend(
        loc="upper left",
        frameon=False,
        ncols=2,
        fontsize=12,
        bbox_to_anchor=(0, 1.01),
    )

    note = (
        "Lower is better · latency normalized to milliseconds"
        if chart["lower_is_better"]
        else "Higher is better · throughput measured in requests per second"
    )
    fig.text(0.5, 0.025, note, ha="center", color="#64748B", fontsize=10)

    fig.tight_layout(rect=(0.03, 0.06, 0.99, 0.98))
    fig.savefig(OUTPUT_DIR / chart["filename"], bbox_inches="tight", facecolor="white")
    plt.close(fig)
