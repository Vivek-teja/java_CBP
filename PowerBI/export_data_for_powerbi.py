#!/usr/bin/env python3
"""
MySQL Data Exporter for Power BI
Exports real-time simulation datasets from MySQL traffic_management_db to CSV files.
"""

import os
import csv
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(SCRIPT_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)

TABLES = [
    'simulation_runs',
    'roads',
    'lanes',
    'vehicles',
    'traffic_snapshots',
    'signal_events',
    'emergency_events',
    'vehicle_events'
]

def export_table(table_name):
    csv_path = os.path.join(DATA_DIR, f"{table_name}.csv")
    try:
        cmd = f"mysql -u root -B -e 'SELECT * FROM {table_name};' traffic_management_db"
        output = subprocess.check_output(cmd, shell=True).decode('utf-8')
        lines = output.strip().split('\n')
        if lines and lines[0]:
            with open(csv_path, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                for line in lines:
                    writer.writerow(line.split('\t'))
            print(f"  [OK] Exported {len(lines)-1:5d} records -> data/{table_name}.csv")
            return len(lines) - 1
        else:
            print(f"  [WARN] Table {table_name} returned 0 rows.")
            return 0
    except subprocess.CalledProcessError as e:
        print(f"  [ERROR] MySQL command failed for {table_name}: {e}")
        return -1
    except Exception as e:
        print(f"  [ERROR] Failed to export {table_name}: {e}")
        return -1

def main():
    print("=" * 60)
    print("Exporting MySQL 'traffic_management_db' to Power BI CSV format")
    print("=" * 60)
    total_records = 0
    for tbl in TABLES:
        c = export_table(tbl)
        if c > 0:
            total_records += c
    print("=" * 60)
    print(f"Export completed: {total_records:,} total records saved to {DATA_DIR}")
    print("=" * 60)

if __name__ == '__main__':
    main()
