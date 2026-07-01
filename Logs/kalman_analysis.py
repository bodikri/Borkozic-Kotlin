#!/usr/bin/env python3
"""Kalman Filter DR Analysis: compare DR_CORRECTION GPS positions vs DR_DEBUG predicted positions."""

import re
import json
import statistics
import os
from collections import defaultdict

# --- File paths ---
LOG_DIR = "/mnt/d/Borkozic_Versions/Borkozic-Kotlin_Oki/Logs"
FILES = [
    ("Test 1", os.path.join(LOG_DIR, "dr_log_2026-06-30_120852.txt")),
    ("Test 2", os.path.join(LOG_DIR, "dr_log_2026-06-30_121154.txt")),
    ("Test 3", os.path.join(LOG_DIR, "dr_log_2026-06-30_121544.txt")),
]

OUT_DOCX = os.path.join(LOG_DIR, "Kalman_Filter_Analysis.docx")
OUT_JSON = os.path.join(LOG_DIR, "Kalman_Filter_Analysis.json")

# --- Regex patterns ---
RE_DEBUG = re.compile(
    r'DR_DEBUG:.*?posN=([-\d.]+), posE=([-\d.]+).*?P_diag=\[([-\d.]+),([-\d.]+),([-\d.]+),([-\d.]+)\].*?biasN=([-\d.]+), biasE=([-\d.]+).*?gpsCorr=#(\d+)'
)
RE_CORRECTION = re.compile(
    r'DR_CORRECTION: gpsPosN=([-\d.]+), gpsPosE=([-\d.]+), gpsVelN=([-\d.]+), gpsVelE=([-\d.]+), acc=([-\d.]+)'
)

def parse_log(filepath):
    """Parse a log file, return list of (type, data_dict) entries."""
    entries = []
    with open(filepath, 'r') as f:
        for line in f:
            m = RE_DEBUG.search(line)
            if m:
                entries.append(('DEBUG', {
                    'posN': float(m.group(1)),
                    'posE': float(m.group(2)),
                    'P_diag': [float(m.group(3)), float(m.group(4)), float(m.group(5)), float(m.group(6))],
                    'biasN': float(m.group(7)),
                    'biasE': float(m.group(8)),
                    'gpsCorr': int(m.group(9)),
                }))
                continue
            m = RE_CORRECTION.search(line)
            if m:
                entries.append(('CORRECTION', {
                    'gpsPosN': float(m.group(1)),
                    'gpsPosE': float(m.group(2)),
                    'gpsVelN': float(m.group(3)),
                    'gpsVelE': float(m.group(4)),
                    'acc': float(m.group(5)),
                }))
    return entries

def analyze_test(name, entries):
    """Analyze one test: pair each CORRECTION with the last DEBUG before it."""
    pairs = []
    last_debug = None

    for etype, data in entries:
        if etype == 'DEBUG':
            last_debug = data
        elif etype == 'CORRECTION' and last_debug is not None:
            # Difference: GPS position - DR predicted position
            dN = data['gpsPosN'] - last_debug['posN']
            dE = data['gpsPosE'] - last_debug['posE']
            dist = (dN**2 + dE**2) ** 0.5

            # P_diag: position uncertainty from Kalman filter
            # P_diag[0] = variance in N, P_diag[1] = variance in E
            # Standard deviation = sqrt(variance)
            p_sigma_n = last_debug['P_diag'][0] ** 0.5
            p_sigma_e = last_debug['P_diag'][1] ** 0.5
            p_sigma_combined = (last_debug['P_diag'][0] + last_debug['P_diag'][1]) ** 0.5

            pairs.append({
                'gpsCorr': last_debug['gpsCorr'],
                'dr_posN': last_debug['posN'],
                'dr_posE': last_debug['posE'],
                'gps_posN': data['gpsPosN'],
                'gps_posE': data['gpsPosE'],
                'diffN': dN,
                'diffE': dE,
                'diffDist': dist,
                'P_diag': last_debug['P_diag'],
                'P_sigma_n': p_sigma_n,
                'P_sigma_e': p_sigma_e,
                'P_sigma_combined': p_sigma_combined,
                'biasN': last_debug['biasN'],
                'biasE': last_debug['biasE'],
                'gpsAcc': data['acc'],
            })

    if not pairs:
        return {
            'name': name,
            'num_corrections': 0,
            'pairs': [],
            'stats': {},
            'bias_growth': [],
            'p_diag_vs_error': [],
        }

    diffs = [p['diffDist'] for p in pairs]
    diffs_n = [p['diffN'] for p in pairs]
    diffs_e = [p['diffE'] for p in pairs]

    stats = {
        'num_corrections': len(pairs),
        'diffDist': {
            'mean': statistics.mean(diffs),
            'median': statistics.median(diffs),
            'min': min(diffs),
            'max': max(diffs),
            'stdev': statistics.stdev(diffs) if len(diffs) > 1 else 0,
        },
        'diffN': {
            'mean': statistics.mean(diffs_n),
            'median': statistics.median(diffs_n),
            'min': min(diffs_n),
            'max': max(diffs_n),
            'stdev': statistics.stdev(diffs_n) if len(diffs_n) > 1 else 0,
        },
        'diffE': {
            'mean': statistics.mean(diffs_e),
            'median': statistics.median(diffs_e),
            'min': min(diffs_e),
            'max': max(diffs_e),
            'stdev': statistics.stdev(diffs_e) if len(diffs_e) > 1 else 0,
        },
    }

    # Bias growth: track biasN/biasE over corrections
    bias_growth = [{
        'corr_idx': i,
        'gpsCorr': p['gpsCorr'],
        'biasN': p['biasN'],
        'biasE': p['biasE'],
        'biasMag': (p['biasN']**2 + p['biasE']**2) ** 0.5,
    } for i, p in enumerate(pairs)]

    # P_diag vs real error: compare Kalman's estimated uncertainty vs actual error
    p_diag_vs_error = [{
        'corr_idx': i,
        'gpsCorr': p['gpsCorr'],
        'P_sigma_n': p['P_sigma_n'],
        'P_sigma_e': p['P_sigma_e'],
        'P_sigma_combined': p['P_sigma_combined'],
        'abs_diffN': abs(p['diffN']),
        'abs_diffE': abs(p['diffE']),
        'diffDist': p['diffDist'],
        'within_1sigma_n': abs(p['diffN']) <= p['P_sigma_n'],
        'within_1sigma_e': abs(p['diffE']) <= p['P_sigma_e'],
        'within_2sigma_n': abs(p['diffN']) <= 2 * p['P_sigma_n'],
        'within_2sigma_e': abs(p['diffE']) <= 2 * p['P_sigma_e'],
    } for i, p in enumerate(pairs)]

    # Consistency metrics
    within_1sigma_count = sum(1 for x in p_diag_vs_error if x['within_1sigma_n'] and x['within_1sigma_e'])
    within_2sigma_count = sum(1 for x in p_diag_vs_error if x['within_2sigma_n'] and x['within_2sigma_e'])
    total = len(p_diag_vs_error)

    stats['consistency'] = {
        'within_1sigma_pct': (within_1sigma_count / total * 100) if total > 0 else 0,
        'within_2sigma_pct': (within_2sigma_count / total * 100) if total > 0 else 0,
        'total': total,
    }

    return {
        'name': name,
        'num_corrections': len(pairs),
        'pairs': pairs,
        'stats': stats,
        'bias_growth': bias_growth,
        'p_diag_vs_error': p_diag_vs_error,
    }


def build_docx(all_results):
    """Build DOCX report."""
    from docx import Document
    from docx.shared import Inches, Pt, Cm, RGBColor
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml.ns import qn

    doc = Document()

    # --- Styles ---
    style = doc.styles['Normal']
    font = style.font
    font.name = 'Calibri'
    font.size = Pt(10)

    # --- Title ---
    title = doc.add_heading('Kalman Filter Dead Reckoning Analysis', level=0)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER

    doc.add_paragraph(
        'Analysis of DR_CORRECTION GPS positions vs DR_DEBUG Kalman-predicted positions.\n'
        'Three test runs compared: position error, P_diag consistency, and bias growth.',
        style='Normal'
    )

    # ============================================================
    # SECTION 1: Per-Test Summary Statistics
    # ============================================================
    doc.add_heading('1. Per-Test Summary Statistics', level=1)

    for result in all_results:
        doc.add_heading(f'Test: {result["name"]}', level=2)
        s = result['stats']
        doc.add_paragraph(f'Number of DR_CORRECTION events: {s["num_corrections"]}')

        # Position error table
        doc.add_heading('Position Error (meters)', level=3)
        table = doc.add_table(rows=6, cols=4, style='Light Grid Accent 1')
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        headers = ['Metric', 'ΔN (North)', 'ΔE (East)', 'Distance']
        for i, h in enumerate(headers):
            cell = table.rows[0].cells[i]
            cell.text = h
            for p in cell.paragraphs:
                for r in p.runs:
                    r.bold = True

        metrics = [
            ('Mean', s['diffN']['mean'], s['diffE']['mean'], s['diffDist']['mean']),
            ('Median', s['diffN']['median'], s['diffE']['median'], s['diffDist']['median']),
            ('Min', s['diffN']['min'], s['diffE']['min'], s['diffDist']['min']),
            ('Max', s['diffN']['max'], s['diffE']['max'], s['diffDist']['max']),
            ('Std Dev', s['diffN']['stdev'], s['diffE']['stdev'], s['diffDist']['stdev']),
        ]
        for row_idx, (label, vn, ve, vd) in enumerate(metrics, 1):
            table.rows[row_idx].cells[0].text = label
            table.rows[row_idx].cells[1].text = f'{vn:.2f}'
            table.rows[row_idx].cells[2].text = f'{ve:.2f}'
            table.rows[row_idx].cells[3].text = f'{vd:.2f}'

        # Consistency table
        doc.add_heading('Kalman Consistency (P_diag vs Real Error)', level=3)
        c = s['consistency']
        table2 = doc.add_table(rows=3, cols=2, style='Light Grid Accent 1')
        table2.alignment = WD_TABLE_ALIGNMENT.CENTER
        table2.rows[0].cells[0].text = 'Metric'
        table2.rows[0].cells[1].text = 'Value'
        for p in table2.rows[0].cells[0].paragraphs:
            for r in p.runs:
                r.bold = True
        for p in table2.rows[0].cells[1].paragraphs:
            for r in p.runs:
                r.bold = True
        table2.rows[1].cells[0].text = 'Within 1σ (both N & E)'
        table2.rows[1].cells[1].text = f'{c["within_1sigma_pct"]:.1f}% ({int(c["within_1sigma_pct"]*c["total"]/100)}/{c["total"]})'
        table2.rows[2].cells[0].text = 'Within 2σ (both N & E)'
        table2.rows[2].cells[1].text = f'{c["within_2sigma_pct"]:.1f}% ({int(c["within_2sigma_pct"]*c["total"]/100)}/{c["total"]})'

        doc.add_paragraph('')  # spacer

    # ============================================================
    # SECTION 2: Cross-Test Comparison
    # ============================================================
    doc.add_heading('2. Cross-Test Comparison', level=1)

    # Mean error comparison
    doc.add_heading('Mean Position Error by Test', level=2)
    table3 = doc.add_table(rows=len(all_results)+1, cols=4, style='Light Grid Accent 1')
    table3.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(['Test', 'Mean ΔN (m)', 'Mean ΔE (m)', 'Mean Distance (m)']):
        cell = table3.rows[0].cells[i]
        cell.text = h
        for p in cell.paragraphs:
            for r in p.runs:
                r.bold = True
    for row_idx, result in enumerate(all_results, 1):
        s = result['stats']
        table3.rows[row_idx].cells[0].text = result['name']
        table3.rows[row_idx].cells[1].text = f'{s["diffN"]["mean"]:.2f}'
        table3.rows[row_idx].cells[2].text = f'{s["diffE"]["mean"]:.2f}'
        table3.rows[row_idx].cells[3].text = f'{s["diffDist"]["mean"]:.2f}'

    # Consistency comparison
    doc.add_heading('Kalman Consistency by Test', level=2)
    table4 = doc.add_table(rows=len(all_results)+1, cols=3, style='Light Grid Accent 1')
    table4.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(['Test', 'Within 1σ (%)', 'Within 2σ (%)']):
        cell = table4.rows[0].cells[i]
        cell.text = h
        for p in cell.paragraphs:
            for r in p.runs:
                r.bold = True
    for row_idx, result in enumerate(all_results, 1):
        c = result['stats']['consistency']
        table4.rows[row_idx].cells[0].text = result['name']
        table4.rows[row_idx].cells[1].text = f'{c["within_1sigma_pct"]:.1f}%'
        table4.rows[row_idx].cells[2].text = f'{c["within_2sigma_pct"]:.1f}%'

    doc.add_paragraph('')

    # ============================================================
    # SECTION 3: Bias Growth Analysis
    # ============================================================
    doc.add_heading('3. Bias Growth Analysis', level=1)
    doc.add_paragraph(
        'The Kalman filter estimates accelerometer bias (biasN, biasE in m/s²). '
        'Bias growth indicates how the filter adapts to sensor drift over time.'
    )

    for result in all_results:
        doc.add_heading(f'Test: {result["name"]}', level=2)
        bg = result['bias_growth']
        if not bg:
            doc.add_paragraph('No bias data available.')
            continue

        # Show first, middle, last bias values
        table5 = doc.add_table(rows=4, cols=4, style='Light Grid Accent 1')
        table5.alignment = WD_TABLE_ALIGNMENT.CENTER
        for i, h in enumerate(['Stage', 'Corr #', 'Bias N (m/s²)', 'Bias E (m/s²)']):
            cell = table5.rows[0].cells[i]
            cell.text = h
            for p in cell.paragraphs:
                for r in p.runs:
                    r.bold = True

        stages = [
            ('Initial', bg[0]),
            ('Middle', bg[len(bg)//2]),
            ('Final', bg[-1]),
        ]
        for row_idx, (label, b) in enumerate(stages, 1):
            table5.rows[row_idx].cells[0].text = label
            table5.rows[row_idx].cells[1].text = str(b['gpsCorr'])
            table5.rows[row_idx].cells[2].text = f'{b["biasN"]:.6f}'
            table5.rows[row_idx].cells[3].text = f'{b["biasE"]:.6f}'

        # Bias magnitude change
        bias_start = (bg[0]['biasN']**2 + bg[0]['biasE']**2) ** 0.5
        bias_end = (bg[-1]['biasN']**2 + bg[-1]['biasE']**2) ** 0.5
        doc.add_paragraph(
            f'Bias magnitude: {bias_start:.6f} → {bias_end:.6f} m/s² '
            f'(Δ = {bias_end - bias_start:+.6f})'
        )

        doc.add_paragraph('')

    # ============================================================
    # SECTION 4: Detailed Correction-by-Correction Tables
    # ============================================================
    doc.add_heading('4. Detailed Correction Data', level=1)

    for result in all_results:
        doc.add_heading(f'Test: {result["name"]}', level=2)
        pairs = result['pairs']
        if not pairs:
            doc.add_paragraph('No correction data.')
            continue

        # Show first 20 and last 5 rows if many
        show_pairs = pairs
        truncated = False
        if len(pairs) > 30:
            show_pairs = pairs[:20]
            truncated = True

        table6 = doc.add_table(rows=len(show_pairs)+1, cols=9, style='Light Grid Accent 1')
        table6.alignment = WD_TABLE_ALIGNMENT.CENTER
        headers = ['Corr#', 'DR posN', 'DR posE', 'GPS posN', 'GPS posE', 'ΔN', 'ΔE', 'Dist', 'P_σ_comb']
        for i, h in enumerate(headers):
            cell = table6.rows[0].cells[i]
            cell.text = h
            for p in cell.paragraphs:
                for r in p.runs:
                    r.bold = True
                    r.font.size = Pt(8)

        for row_idx, p in enumerate(show_pairs, 1):
            vals = [
                str(p['gpsCorr']),
                f'{p["dr_posN"]:.1f}',
                f'{p["dr_posE"]:.1f}',
                f'{p["gps_posN"]:.1f}',
                f'{p["gps_posE"]:.1f}',
                f'{p["diffN"]:.1f}',
                f'{p["diffE"]:.1f}',
                f'{p["diffDist"]:.1f}',
                f'{p["P_sigma_combined"]:.2f}',
            ]
            for col_idx, v in enumerate(vals):
                table6.rows[row_idx].cells[col_idx].text = v
                for par in table6.rows[row_idx].cells[col_idx].paragraphs:
                    for r in par.runs:
                        r.font.size = Pt(8)

        if truncated:
            doc.add_paragraph(f'... and {len(pairs) - 20} more rows (see JSON for full data).')

            # Also show last 5
            doc.add_paragraph('Last 5 corrections:')
            last5 = pairs[-5:]
            table6b = doc.add_table(rows=len(last5)+1, cols=9, style='Light Grid Accent 1')
            table6b.alignment = WD_TABLE_ALIGNMENT.CENTER
            for i, h in enumerate(headers):
                cell = table6b.rows[0].cells[i]
                cell.text = h
                for p in cell.paragraphs:
                    for r in p.runs:
                        r.bold = True
                        r.font.size = Pt(8)
            for row_idx, p in enumerate(last5, 1):
                vals = [
                    str(p['gpsCorr']),
                    f'{p["dr_posN"]:.1f}', f'{p["dr_posE"]:.1f}',
                    f'{p["gps_posN"]:.1f}', f'{p["gps_posE"]:.1f}',
                    f'{p["diffN"]:.1f}', f'{p["diffE"]:.1f}',
                    f'{p["diffDist"]:.1f}', f'{p["P_sigma_combined"]:.2f}',
                ]
                for col_idx, v in enumerate(vals):
                    table6b.rows[row_idx].cells[col_idx].text = v
                    for par in table6b.rows[row_idx].cells[col_idx].paragraphs:
                        for r in par.runs:
                            r.font.size = Pt(8)

        doc.add_paragraph('')

    # ============================================================
    # SECTION 5: P_diag vs Real Error Analysis
    # ============================================================
    doc.add_heading('5. P_diag vs Real Error (Detailed)', level=1)
    doc.add_paragraph(
        'Comparison of Kalman filter\'s estimated position uncertainty (P_diag) '
        'against the actual GPS correction error. Ideally, ~68% of errors should '
        'fall within 1σ and ~95% within 2σ for a well-tuned filter.'
    )

    for result in all_results:
        doc.add_heading(f'Test: {result["name"]}', level=2)
        pve = result['p_diag_vs_error']
        if not pve:
            doc.add_paragraph('No data.')
            continue

        show = pve[:15] if len(pve) > 15 else pve
        table7 = doc.add_table(rows=len(show)+1, cols=8, style='Light Grid Accent 1')
        table7.alignment = WD_TABLE_ALIGNMENT.CENTER
        headers = ['Corr#', 'P_σ_N', '|ΔN|', 'In 1σ?', 'P_σ_E', '|ΔE|', 'In 1σ?', 'Dist']
        for i, h in enumerate(headers):
            cell = table7.rows[0].cells[i]
            cell.text = h
            for p in cell.paragraphs:
                for r in p.runs:
                    r.bold = True
                    r.font.size = Pt(8)

        for row_idx, x in enumerate(show, 1):
            vals = [
                str(x['gpsCorr']),
                f'{x["P_sigma_n"]:.2f}',
                f'{x["abs_diffN"]:.2f}',
                '✓' if x['within_1sigma_n'] else '✗',
                f'{x["P_sigma_e"]:.2f}',
                f'{x["abs_diffE"]:.2f}',
                '✓' if x['within_1sigma_e'] else '✗',
                f'{x["diffDist"]:.2f}',
            ]
            for col_idx, v in enumerate(vals):
                table7.rows[row_idx].cells[col_idx].text = v
                for par in table7.rows[row_idx].cells[col_idx].paragraphs:
                    for r in par.runs:
                        r.font.size = Pt(8)

        if len(pve) > 15:
            doc.add_paragraph(f'... and {len(pve) - 15} more rows (see JSON for full data).')

        doc.add_paragraph('')

    # ============================================================
    # SECTION 6: Overall Conclusions
    # ============================================================
    doc.add_heading('6. Summary & Conclusions', level=1)

    # Aggregate stats
    all_diffs = []
    for result in all_results:
        all_diffs.extend([p['diffDist'] for p in result['pairs']])

    if all_diffs:
        doc.add_paragraph(f'Total corrections analyzed across all tests: {len(all_diffs)}')
        doc.add_paragraph(f'Overall mean position error: {statistics.mean(all_diffs):.2f} m')
        doc.add_paragraph(f'Overall median position error: {statistics.median(all_diffs):.2f} m')
        doc.add_paragraph(f'Overall max position error: {max(all_diffs):.2f} m')
        doc.add_paragraph(f'Overall min position error: {min(all_diffs):.2f} m')

    # Consistency summary
    total_1sigma = sum(r['stats']['consistency']['within_1sigma_pct'] * r['stats']['consistency']['total'] / 100 for r in all_results)
    total_all = sum(r['stats']['consistency']['total'] for r in all_results)
    total_2sigma = sum(r['stats']['consistency']['within_2sigma_pct'] * r['stats']['consistency']['total'] / 100 for r in all_results)

    doc.add_paragraph(f'Overall within 1σ: {total_1sigma/total_all*100:.1f}% ({int(total_1sigma)}/{total_all})')
    doc.add_paragraph(f'Overall within 2σ: {total_2sigma/total_all*100:.1f}% ({int(total_2sigma)}/{total_all})')

    doc.add_paragraph('')
    p = doc.add_paragraph()
    p.add_run('Note: ').bold = True
    p.add_run(
        'For a well-tuned Kalman filter, ~68% of errors should fall within 1σ and ~95% within 2σ. '
        'Values significantly below these thresholds suggest the filter is overconfident (P_diag too small); '
        'values above suggest the filter is too conservative (P_diag too large).'
    )

    doc.save(OUT_DOCX)
    print(f"DOCX saved to: {OUT_DOCX}")


def main():
    print("=" * 60)
    print("Kalman Filter DR Analysis")
    print("=" * 60)

    all_results = []

    for name, filepath in FILES:
        print(f"\n--- {name} ---")
        print(f"  File: {filepath}")
        entries = parse_log(filepath)
        debug_count = sum(1 for e in entries if e[0] == 'DEBUG')
        corr_count = sum(1 for e in entries if e[0] == 'CORRECTION')
        print(f"  DR_DEBUG lines: {debug_count}")
        print(f"  DR_CORRECTION lines: {corr_count}")

        result = analyze_test(name, entries)
        all_results.append(result)

        s = result['stats']
        print(f"  Paired corrections: {s['num_corrections']}")
        if s:
            print(f"  Mean error:  ΔN={s['diffN']['mean']:.2f}m  ΔE={s['diffE']['mean']:.2f}m  Dist={s['diffDist']['mean']:.2f}m")
            print(f"  Median error: ΔN={s['diffN']['median']:.2f}m  ΔE={s['diffE']['median']:.2f}m  Dist={s['diffDist']['median']:.2f}m")
            print(f"  Max error:   ΔN={s['diffN']['max']:.2f}m  ΔE={s['diffE']['max']:.2f}m  Dist={s['diffDist']['max']:.2f}m")
            print(f"  Min error:   ΔN={s['diffN']['min']:.2f}m  ΔE={s['diffE']['min']:.2f}m  Dist={s['diffDist']['min']:.2f}m")
            c = s['consistency']
            print(f"  Within 1σ: {c['within_1sigma_pct']:.1f}%  |  Within 2σ: {c['within_2sigma_pct']:.1f}%")

            # Bias growth
            bg = result['bias_growth']
            if bg:
                print(f"  Bias N: {bg[0]['biasN']:.6f} → {bg[-1]['biasN']:.6f}")
                print(f"  Bias E: {bg[0]['biasE']:.6f} → {bg[-1]['biasE']:.6f}")

    # Save JSON
    # Strip pairs for cleaner JSON (keep stats, bias_growth, p_diag_vs_error)
    json_output = []
    for r in all_results:
        json_output.append({
            'name': r['name'],
            'num_corrections': r['num_corrections'],
            'stats': r['stats'],
            'bias_growth': r['bias_growth'],
            'p_diag_vs_error': r['p_diag_vs_error'],
            'pairs': r['pairs'],  # full data
        })

    with open(OUT_JSON, 'w') as f:
        json.dump(json_output, f, indent=2, default=str)
    print(f"\nJSON saved to: {OUT_JSON}")

    # Build DOCX
    print("\nBuilding DOCX report...")
    try:
        build_docx(all_results)
    except ImportError:
        print("ERROR: python-docx not installed. Install with: pip install python-docx")
        raise

    print("\nDone!")


if __name__ == '__main__':
    main()
