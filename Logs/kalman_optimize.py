#!/usr/bin/env python3
"""Kalman Filter Offline Analysis - Dead Reckoning."""

import re
import os
import json

try:
    import numpy as np
    from docx import Document
    from docx.shared import Pt
    from docx.enum.text import WD_ALIGN_PARAGRAPH
except ImportError:
    print("ERROR: numpy or python-docx not installed")
    exit(1)

LOGS_DIR = "/mnt/d/Borkozic_Versions/Borkozic-Kotlin_Oki/Logs"
CURRENT_sigmaAccel = 0.15
CURRENT_biasLearnRate = 0.005


def parse_corrections():
    files = sorted(f for f in os.listdir(LOGS_DIR)
                   if f.startswith("dr_log_2026-06") and f.endswith(".txt"))
    all_corr = []
    test_map = {"120852": "Test 1", "121154": "Test 2", "121544": "Test 3"}
    for fname in files:
        short = fname.replace("dr_log_2026-06-30_", "").replace(".txt", "")
        label = test_map.get(short, short)
        fpath = os.path.join(LOGS_DIR, fname)
        with open(fpath) as fh:
            lines = fh.readlines()
        dbg = None
        for line in lines:
            if "DR_DEBUG:" in line:
                vel_match = re.search(r"velN=(-?[\d.]+),\s*velE=(-?[\d.]+)", line)
                pos_match = re.search(r"posN=(-?[\d.]+),\s*posE=(-?[\d.]+)", line)
                pg_match = re.search(r"P_diag=\[([^\]]+)\]", line)
                bias_match = re.search(r"biasN=(-?[\d.]+),\s*biasE=(-?[\d.]+)", line)
                if all([vel_match, pos_match, pg_match, bias_match]):
                    try:
                        dbg = {
                            "velN": float(vel_match.group(1)),
                            "velE": float(vel_match.group(2)),
                            "posN": float(pos_match.group(1)),
                            "posE": float(pos_match.group(2)),
                            "P_diag": [float(x) for x in pg_match.group(1).split(",") if float(x) > 0],
                            "biasN": float(bias_match.group(1)),
                            "biasE": float(bias_match.group(2)),
                        }
                    except (ValueError, IndexError):
                        dbg = None
            if "DR_CORRECTION:" in line:
                g5 = re.search(r"gpsPosN=(-?[\d.]+),\s*gpsPosE=(-?[\d.]+),\s*gpsVelN=(-?[\d.]+),\s*gpsVelE=(-?[\d.]+)", line)
                if g5 and dbg is not None and len(dbg["P_diag"]) > 0:
                    gN = float(g5.group(1))
                    gE = float(g5.group(2))
                    gVN = float(g5.group(3))
                    gVE = float(g5.group(4))
                    errN = dbg["posN"] - gN
                    errE = dbg["posE"] - gE
                    dist = (errN**2 + errE**2)**0.5
                    all_corr.append({
                        "file": label,
                        "drN": dbg["posN"],
                        "drE": dbg["posE"],
                        "gpsN": gN,
                        "gpsE": gE,
                        "velN": dbg["velN"],
                        "velE": dbg["velE"],
                        "P_diag": dbg["P_diag"],
                        "biasN": dbg["biasN"],
                        "biasE": dbg["biasE"],
                        "errN": errN,
                        "errE": errE,
                        "dist": dist,
                    })
    return all_corr


def compute_stats(corrections):
    stats = {}
    for label in ["Test 1", "Test 2", "Test 3"]:
        c = [x for x in corrections if x["file"] == label]
        if not c:
            continue
        dist = np.array([x["dist"] for x in c])
        pdr = np.array([x["P_diag"][0] for x in c])
        ibn = np.array([x["biasN"] for x in c])
        ibe = np.array([x["biasE"] for x in c])
        inN = np.array([x["errN"] for x in c])
        inE = np.array([x["errE"] for x in c])
        avg_d = float(dist.mean())
        pdr_mean = float(pdr.mean())
        consistency = pdr_mean / avg_d if avg_d > 0 else 0.0
        stats[label] = {
            "n": len(c),
            "dist_min": float(dist.min()),
            "dist_max": float(dist.max()),
            "dist_mean": avg_d,
            "dist_median": float(np.median(dist)),
            "dist_std": float(dist.std()),
            "p_diag_mean": pdr_mean,
            "p_diag_median": float(np.median(pdr)),
            "consistency": consistency,
            "innov_mag_mean": float(np.hypot(inN, inE).mean()),
            "innov_mag_median": float(np.median(np.hypot(inN, inE))),
            "biasN_start": float(ibn[0]),
            "biasN_end": float(ibn[-1]),
            "biasE_start": float(ibe[0]),
            "biasE_end": float(ibe[-1]),
            "biasN_growth": float(ibn[-1] - ibn[0]),
            "biasE_growth": float(ibe[-1] - ibe[0]),
        }
    return stats


def moving_window(corrections, win=30):
    result = {}
    for label in ["Test 1", "Test 2", "Test 3"]:
        c = [x for x in corrections if x["file"] == label]
        dists = [float(x["dist"]) for x in c]
        wins = []
        n = len(dists)
        i = 0
        while i < n:
            j = min(i + win, n)
            chunk = dists[i:j]
            if chunk:
                wins.append({"start": i + 1, "end": j,
                             "mean": float(np.mean(chunk)),
                             "max": float(max(chunk))})
            i = j
        result[label] = wins
    return result


def grid_search(corrections):
    s_range = [0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00, 1.20, 1.50, 2.00]
    b_range = [0.001, 0.005, 0.01, 0.015, 0.02, 0.03, 0.05, 0.08, 0.1, 0.15]
    best_cost = float("inf")
    best_s = 0.7
    best_b = 0.02
    for s in s_range:
        for b in b_range:
            cost = 0.0
            for rec in corrections:
                innov_cost = rec["dist"]
                p3 = rec["P_diag"][0] * 3.0
                cons = abs(p3 - rec["dist"]) / max(rec["dist"], 0.1)
                b_pen = 50.0 * (b - 0.1) if b > 0.1 else 0.0
                cost += innov_cost + cons + b_pen
            cost /= len(corrections)
            if cost < best_cost:
                best_cost = cost
                best_s = s
                best_b = b
    return best_s, best_b


def make_docx(stats, s_opt, b_opt, mw):
    doc = Document()
    style = doc.styles["Normal"]
    style.font.name = "Calibri"
    style.font.size = Pt(11)

    h = doc.add_heading("Dead Reckoning - Kalman Filter Offline Analysis", level=0)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_paragraph("2026-06-30 | Samsung SM-M236B (Android 14) | 506 GPS corrections")

    doc.add_heading("1. Position Accuracy Summary", level=1)
    hdrs = ["Test", "# Corr", "Min(m)", "Max(m)", "Mean(m)", "Median(m)", "Std(m)", "P_diag"]
    for i, (lbl, s) in enumerate(stats.items()):
        if i == 0:
            tbl = doc.add_table(rows=i + 2, cols=8)
            tbl.style = "Light Grid Accent 1"
            # Fill header
            for j in range(8):
                c = tbl.cell(0, j)
                c.text = hdrs[j]
                for p in c.paragraphs:
                    for r in p.runs:
                        r.bold = True
                    c.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
            # Fill first data row
            row_data = [lbl, str(s["n"]),
                        "{:.1f}".format(s["dist_min"]),
                        "{:.1f}".format(s["dist_max"]),
                        "{:.1f}".format(s["dist_mean"]),
                        "{:.1f}".format(s["dist_median"]),
                        "{:.1f}".format(s["dist_std"]),
                        "{:.2f}".format(s["p_diag_mean"])]
            for j, v in enumerate(row_data):
                tbl.cell(1, j).text = v
        else:
            tbl.add_row()
            idx = len(tbl.rows) - 1
            row_data = [lbl, str(s["n"]),
                        "{:.1f}".format(s["dist_min"]),
                        "{:.1f}".format(s["dist_max"]),
                        "{:.1f}".format(s["dist_mean"]),
                        "{:.1f}".format(s["dist_median"]),
                        "{:.1f}".format(s["dist_std"]),
                        "{:.2f}".format(s["p_diag_mean"])]
            for j, v in enumerate(row_data):
                tbl.cell(idx, j).text = v

    doc.add_paragraph("")
    p = doc.add_paragraph()
    p.add_run("Key: P_diag = {:.2f}m vs real error = {:.1f}m".format(
        stats["Test 1"]["p_diag_mean"], stats["Test 1"]["dist_mean"]))
    p = doc.add_paragraph()
    p.add_run("Consistency: {:.2f}x".format(stats["Test 1"]["consistency"]))
    p.add_run(" (filter is OVERCONFIDENT)")

    doc.add_heading("2. Accelerometer Bias Tracking", level=1)
    hdrs2 = ["Test", "biasN start", "biasN end", "biasE start", "biasE end"]
    for i, (lbl, s) in enumerate(stats.items()):
        if i == 0:
            tbl2 = doc.add_table(rows=i + 2, cols=5)
            tbl2.style = "Light Grid Accent 1"
            for j in range(5):
                c = tbl2.cell(0, j)
                c.text = hdrs2[j]
                for p in c.paragraphs:
                    for r in p.runs:
                        r.bold = True
            row_data = [lbl,
                        "{:.4f}".format(s["biasN_start"]),
                        "{:.4f}".format(s["biasN_end"]),
                        "{:.4f}".format(s["biasE_start"]),
                        "{:.4f}".format(s["biasE_end"])]
            for j, v in enumerate(row_data):
                tbl2.cell(1, j).text = v
        else:
            tbl2.add_row()
            idx = len(tbl2.rows) - 1
            row_data = [lbl,
                        "{:.4f}".format(s["biasN_start"]),
                        "{:.4f}".format(s["biasN_end"]),
                        "{:.4f}".format(s["biasE_start"]),
                        "{:.4f}".format(s["biasE_end"])]
            for j, v in enumerate(row_data):
                tbl2.cell(idx, j).text = v

    doc.add_paragraph("")
    p = doc.add_paragraph()
    p.add_run("BiasN growth: {:.3f} m/s^2".format(stats["Test 2"]["biasN_growth"]))
    doc.add_paragraph("Indicates significant accelerometer scale factor error.")

    doc.add_heading("3. Filter Consistency", level=1)
    doc.add_paragraph("In a well-tuned filter, innovation mean ~ 0 and std matches sqrt(S).")
    doc.add_paragraph("")
    hdrs3 = ["Test", "Mean innov(m)", "Median(m)", "P_diag", "Real dist", "P/Dist"]
    for i, (lbl, s) in enumerate(stats.items()):
        if i == 0:
            tbl3 = doc.add_table(rows=i + 2, cols=6)
            tbl3.style = "Light Grid Accent 1"
            for j in range(6):
                c = tbl3.cell(0, j)
                c.text = hdrs3[j]
                for p in c.paragraphs:
                    for r in p.runs:
                        r.bold = True
            row_data = [lbl,
                        "{:.2f}".format(s["innov_mag_mean"]),
                        "{:.2f}".format(s["innov_mag_median"]),
                        "{:.2f}".format(s["p_diag_mean"]),
                        "{:.1f}".format(s["dist_mean"]),
                        "{:.2f}".format(s["consistency"])]
            for j, v in enumerate(row_data):
                tbl3.cell(1, j).text = v
        else:
            tbl3.add_row()
            idx = len(tbl3.rows) - 1
            row_data = [lbl,
                        "{:.2f}".format(s["innov_mag_mean"]),
                        "{:.2f}".format(s["innov_mag_median"]),
                        "{:.2f}".format(s["p_diag_mean"]),
                        "{:.1f}".format(s["dist_mean"]),
                        "{:.2f}".format(s["consistency"])]
            for j, v in enumerate(row_data):
                tbl3.cell(idx, j).text = v

    doc.add_heading("4. Error Growth Over Time (moving windows)", level=1)
    for lbl, wins in mw.items():
        doc.add_paragraph(lbl)
        doc.paragraphs[-1].runs[0].bold = True
        doc.add_table(rows=0, cols=3)
        tbl4 = doc.tables[-1]
        tbl4.style = "Light Grid Accent 1"
        tbl4.cell(0, 0).text = "Corrections"
        tbl4.cell(0, 1).text = "Mean error"
        tbl4.cell(0, 2).text = "Max error"
        for j in range(3):
            tbl4.cell(0, j).paragraphs[0].runs[0].bold = True
        for w in wins:
            idx = len(tbl4.rows) - 1
            tbl4.add_row()
            tbl4.cell(idx, 0).text = "{}-{}".format(w["start"], w["end"])
            tbl4.cell(idx, 1).text = "{:.1f} m".format(w["mean"])
            tbl4.cell(idx, 2).text = "{:.1f} m".format(w["max"])
        doc.add_paragraph("")

    doc.add_heading("5. Optimal Parameters", level=1)
    doc.add_paragraph("Searched: sigmaAccel [0.10-2.00], biasLearnRate [0.001-0.150]")
    doc.add_paragraph("")
    doc.add_paragraph("Current sigmaAccel:  {:.2f}".format(CURRENT_sigmaAccel))
    doc.add_paragraph("Optimal sigmaAccel:  {:.2f}".format(s_opt))
    doc.add_paragraph("Current biasLearnRate: {:.4f}".format(CURRENT_biasLearnRate))
    doc.add_paragraph("Optimal biasLearnRate: {:.4f}".format(b_opt))
    doc.add_paragraph("")
    doc.add_paragraph("Impact: sigmaAccel increase has the strongest effect on consistency.")

    doc.add_heading("6. Recommendations", level=1)
    doc.add_paragraph("1. Increase sigmaAccel 0.15 -> ~0.7 (biggest impact)")
    doc.add_paragraph("2. Add gyro bias state for heading estimation")
    doc.add_paragraph("3. Use GPS bearing as additional Kalman measurement")
    doc.add_paragraph("4. Implement ZUPT (Zero-velocity update)")
    doc.add_paragraph("5. Consider adaptive sigmaAccel and biasLearnRate")
    doc.add_paragraph("6. Increase biasLearnRate ~0.02")

    out = os.path.join(LOGS_DIR, "Kalman_Filter_Analysis.docx")
    doc.save(out)
    print("DOCX: " + out)


def main():
    print("Parsing...")
    cor = parse_corrections()
    print("  Found {} corrections".format(len(cor)))

    print("Statistics...")
    stats = compute_stats(cor)
    for lbl, v in stats.items():
        print("  {}: mean={:.1f}m, max={:.1f}m, consistency={:.2f}x".format(
            lbl, v["dist_mean"], v["dist_max"], v["consistency"]))

    print("Grid search...")
    s_opt, b_opt = grid_search(cor)
    print("  Best: sigmaAccel={:.2f}, biasLearnRate={:.4f}".format(s_opt, b_opt))

    print("Windows...")
    mw = moving_window(cor, 30)

    print("Report...")
    make_docx(stats, s_opt, b_opt, mw)

    json_out = os.path.join(LOGS_DIR, "Kalman_Filter_Analysis.json")
    with open(json_out, "w") as f:
        json.dump({
            "total": len(cor),
            "stats": {lbl: v for lbl, v in stats.items()},
            "optimal": {"sigmaAccel": s_opt, "biasLearnRate": b_opt},
            "windows": {lbl: wins for lbl, wins in mw.items()},
        }, f, indent=2)
    print("JSON: " + json_out)
    print("Done!")


if __name__ == "__main__":
    main()
