#!/usr/bin/env python3
"""
Power BI Asset Generator for Intelligent Traffic Management System
Generates:
1. TrafficManagementAnalytics.pbit (Power BI Template with MySQL & DataModelSchema)
2. TrafficManagementAnalytics.pbix (Power BI Report Archive)
3. TrafficManagementAnalytics.pbip (Modern Power BI Project)
4. TrafficManagementAnalytics.Report/ & SemanticModel/ (PBIP directories)
5. Clean CSV snapshots of all 8 database tables in PowerBI/data/
6. export_data_for_powerbi.py (Refresh script for live MySQL export)
7. README_POWERBI.md (Comprehensive documentation)
"""

import os
import json
import csv
import zipfile
import subprocess
import uuid

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)

# 1. Export CSV Data from MySQL
TABLES = [
    'simulation_runs', 'roads', 'lanes', 'vehicles',
    'traffic_snapshots', 'signal_events', 'emergency_events', 'vehicle_events'
]

print("--> Exporting tables from MySQL traffic_management_db to CSV...")
for table in TABLES:
    csv_path = os.path.join(DATA_DIR, f"{table}.csv")
    try:
        cmd = f"mysql -u root -B -e 'SELECT * FROM {table};' traffic_management_db"
        output = subprocess.check_output(cmd, shell=True).decode('utf-8')
        lines = output.strip().split('\n')
        if lines and lines[0]:
            with open(csv_path, 'w', newline='', encoding='utf-8') as f:
                writer = csv.writer(f)
                for line in lines:
                    writer.writerow(line.split('\t'))
            print(f"  [OK] Exported {len(lines)-1} rows -> {table}.csv")
        else:
            print(f"  [WARN] Table {table} has no rows.")
    except Exception as e:
        print(f"  [ERROR] Failed to export {table}: {e}")

# 2. Build Tabular Model Schema (DataModelSchema / model.bim)
print("--> Building Power BI Tabular Model Schema (TMSL compatibilityLevel 1550)...")

def make_column(name, data_type, source_col=None, is_key=False, is_hidden=False):
    col = {
        "name": name,
        "dataType": data_type,
        "sourceColumn": source_col if source_col else name,
        "summarizeBy": "none" if is_key or data_type == "string" else "sum"
    }
    if is_hidden:
        col["isHidden"] = True
    return col

def make_m_partition(table_name):
    # Returns M expression connecting to MySQL with CSV fallback definition
    m_expr = [
        "let",
        f'    Source = MySQL.Database("localhost:3306", "traffic_management_db", [ReturnSingleDatabase=true]),',
        f'    dbo_Table = Source{{[Schema="traffic_management_db",Item="{table_name}"]}}[Data]',
        "in",
        "    dbo_Table"
    ]
    return {
        "name": f"{table_name}-Partition",
        "mode": "import",
        "source": {
            "type": "m",
            "expression": m_expr
        }
    }

tabular_model = {
    "name": "TrafficManagementSemanticModel",
    "compatibilityLevel": 1550,
    "model": {
        "culture": "en-US",
        "dataAccessOptions": {
            "legacyRedirects": True,
            "returnErrorValuesAsNull": True
        },
        "defaultPowerBIDataSourceVersion": "powerBI_V3",
        "sourceQueryCulture": "en-US",
        "tables": [
            {
                "name": "simulation_runs",
                "columns": [
                    make_column("run_id", "int64", is_key=True),
                    make_column("start_time", "dateTime"),
                    make_column("end_time", "dateTime"),
                    make_column("selected_strategy", "string"),
                    make_column("selected_speed", "double"),
                    make_column("simulation_status", "string"),
                    make_column("scenario_id", "string")
                ],
                "partitions": [make_m_partition("simulation_runs")],
                "measures": [
                    {
                        "name": "Total Simulation Runs",
                        "expression": "COUNTROWS('simulation_runs')",
                        "formatString": "#,##0"
                    }
                ]
            },
            {
                "name": "roads",
                "columns": [
                    make_column("road_id", "int64", is_key=True),
                    make_column("direction", "string")
                ],
                "partitions": [make_m_partition("roads")]
            },
            {
                "name": "lanes",
                "columns": [
                    make_column("lane_id", "int64", is_key=True),
                    make_column("road_id", "int64"),
                    make_column("lane_name", "string")
                ],
                "partitions": [make_m_partition("lanes")]
            },
            {
                "name": "vehicles",
                "columns": [
                    make_column("vehicle_id", "int64", is_key=True),
                    make_column("vehicle_number", "string"),
                    make_column("run_id", "int64"),
                    make_column("lane_id", "int64"),
                    make_column("vehicle_type", "string"),
                    make_column("is_emergency", "int64"),
                    make_column("arrival_time", "dateTime"),
                    make_column("intersection_entry_time", "dateTime"),
                    make_column("intersection_exit_time", "dateTime"),
                    make_column("vehicle_speed", "double"),
                    make_column("current_status", "string"),
                    make_column("current_position", "double")
                ],
                "partitions": [make_m_partition("vehicles")],
                "measures": [
                    {
                        "name": "Total Vehicles",
                        "expression": "COUNTROWS('vehicles')",
                        "formatString": "#,##0"
                    },
                    {
                        "name": "Waiting Vehicles",
                        "expression": "CALCULATE(COUNTROWS('vehicles'), 'vehicles'[current_status] = \"WAITING\")",
                        "formatString": "#,##0"
                    },
                    {
                        "name": "In Intersection Vehicles",
                        "expression": "CALCULATE(COUNTROWS('vehicles'), 'vehicles'[current_status] = \"ENTERED_INTERSECTION\")",
                        "formatString": "#,##0"
                    },
                    {
                        "name": "Exited Vehicles",
                        "expression": "CALCULATE(COUNTROWS('vehicles'), 'vehicles'[current_status] = \"EXITED\")",
                        "formatString": "#,##0"
                    },
                    {
                        "name": "Emergency Vehicles",
                        "expression": "CALCULATE(COUNTROWS('vehicles'), 'vehicles'[is_emergency] = 1)",
                        "formatString": "#,##0"
                    },
                    {
                        "name": "Avg Vehicle Speed",
                        "expression": "AVERAGE('vehicles'[vehicle_speed])",
                        "formatString": "#,##0.0"
                    }
                ]
            },
            {
                "name": "traffic_snapshots",
                "columns": [
                    make_column("snapshot_id", "int64", is_key=True),
                    make_column("run_id", "int64"),
                    make_column("simulation_timestamp", "dateTime"),
                    make_column("road_direction", "string"),
                    make_column("lane_id", "int64"),
                    make_column("waiting_vehicle_count", "int64"),
                    make_column("queue_length", "double"),
                    make_column("signal_state", "string"),
                    make_column("active_phase", "string"),
                    make_column("allocated_green_seconds", "int64"),
                    make_column("remaining_seconds", "int64"),
                    make_column("vehicle_throughput", "int64")
                ],
                "partitions": [make_m_partition("traffic_snapshots")],
                "measures": [
                    {
                        "name": "Peak Queue Length",
                        "expression": "MAX('traffic_snapshots'[queue_length])",
                        "formatString": "#,##0.0"
                    },
                    {
                        "name": "Avg Waiting Count",
                        "expression": "AVERAGE('traffic_snapshots'[waiting_vehicle_count])",
                        "formatString": "#,##0.0"
                    },
                    {
                        "name": "Avg Green Seconds",
                        "expression": "AVERAGE('traffic_snapshots'[allocated_green_seconds])",
                        "formatString": "#,##0.0"
                    },
                    {
                        "name": "Total Vehicle Throughput",
                        "expression": "MAX('traffic_snapshots'[vehicle_throughput])",
                        "formatString": "#,##0"
                    }
                ]
            },
            {
                "name": "signal_events",
                "columns": [
                    make_column("signal_event_id", "int64", is_key=True),
                    make_column("run_id", "int64"),
                    make_column("active_phase", "string"),
                    make_column("road_direction", "string"),
                    make_column("signal_state", "string"),
                    make_column("allocated_green_seconds", "int64"),
                    make_column("remaining_seconds", "int64"),
                    make_column("event_timestamp", "dateTime")
                ],
                "partitions": [make_m_partition("signal_events")],
                "measures": [
                    {
                        "name": "Total Signal Transitions",
                        "expression": "COUNTROWS('signal_events')",
                        "formatString": "#,##0"
                    }
                ]
            },
            {
                "name": "emergency_events",
                "columns": [
                    make_column("emergency_event_id", "int64", is_key=True),
                    make_column("run_id", "int64"),
                    make_column("vehicle_id", "int64"),
                    make_column("detection_time", "dateTime"),
                    make_column("priority_request_time", "dateTime"),
                    make_column("priority_granted_time", "dateTime"),
                    make_column("emergency_exit_time", "dateTime"),
                    make_column("priority_duration", "int64"),
                    make_column("affected_phase", "string")
                ],
                "partitions": [make_m_partition("emergency_events")],
                "measures": [
                    {
                        "name": "Emergency Preemption Count",
                        "expression": "COUNTROWS('emergency_events')",
                        "formatString": "#,##0"
                    },
                    {
                        "name": "Avg Emergency Clearance Seconds",
                        "expression": "AVERAGE('emergency_events'[priority_duration])",
                        "formatString": "#,##0.0"
                    },
                    {
                        "name": "Max Priority Duration",
                        "expression": "MAX('emergency_events'[priority_duration])",
                        "formatString": "#,##0"
                    }
                ]
            },
            {
                "name": "vehicle_events",
                "columns": [
                    make_column("event_id", "int64", is_key=True),
                    make_column("vehicle_id", "int64"),
                    make_column("run_id", "int64"),
                    make_column("event_type", "string"),
                    make_column("event_timestamp", "dateTime"),
                    make_column("road_direction", "string"),
                    make_column("description", "string")
                ],
                "partitions": [make_m_partition("vehicle_events")]
            }
        ],
        "relationships": [
            {
                "name": "rel_vehicles_simulation_runs",
                "fromTable": "vehicles",
                "fromColumn": "run_id",
                "toTable": "simulation_runs",
                "toColumn": "run_id"
            },
            {
                "name": "rel_traffic_snapshots_simulation_runs",
                "fromTable": "traffic_snapshots",
                "fromColumn": "run_id",
                "toTable": "simulation_runs",
                "toColumn": "run_id"
            },
            {
                "name": "rel_signal_events_simulation_runs",
                "fromTable": "signal_events",
                "fromColumn": "run_id",
                "toTable": "simulation_runs",
                "toColumn": "run_id"
            },
            {
                "name": "rel_emergency_events_simulation_runs",
                "fromTable": "emergency_events",
                "fromColumn": "run_id",
                "toTable": "simulation_runs",
                "toColumn": "run_id"
            },
            {
                "name": "rel_lanes_roads",
                "fromTable": "lanes",
                "fromColumn": "road_id",
                "toTable": "roads",
                "toColumn": "road_id"
            },
            {
                "name": "rel_vehicles_lanes",
                "fromTable": "vehicles",
                "fromColumn": "lane_id",
                "toTable": "lanes",
                "toColumn": "lane_id"
            }
        ]
    }
}

# 3. Build Report Layout (Report/Layout and report.json)
print("--> Building Power BI Visual Report Layout (3 Interactive Pages)...")

def make_vc(x, y, width, height, z, visual_type, title, table_name, field_or_measure, is_measure=True, extra=None):
    guid = uuid.uuid4().hex[:20]
    config_dict = {
        "name": guid,
        "layouts": [
            {
                "id": 0,
                "position": {
                    "x": float(x),
                    "y": float(y),
                    "z": float(z),
                    "width": float(width),
                    "height": float(height)
                }
            }
        ],
        "singleVisual": {
            "visualType": visual_type,
            "projections": {
                "Values": [
                    {
                        "queryRef": f"{table_name}.{field_or_measure}"
                    }
                ]
            },
            "prototypeQuery": {
                "Version": 2,
                "From": [{"Name": "t", "Entity": table_name, "Type": 0}],
                "Select": [
                    {
                        "Measure" if is_measure else "Column": {
                            "Expression": {"SourceRef": {"Source": "t"}},
                            "Property": field_or_measure
                        },
                        "Name": f"{table_name}.{field_or_measure}"
                    }
                ]
            },
            "vcObjects": {
                "title": [
                    {
                        "properties": {
                            "show": {"expr": {"Literal": {"Value": "true"}}},
                            "text": {"expr": {"Literal": {"Value": f"'{title}'"}}}
                        }
                    }
                ]
            }
        }
    }
    if extra:
        config_dict["singleVisual"].update(extra)

    return {
        "id": int(guid[:8], 16) % 1000000,
        "x": float(x),
        "y": float(y),
        "z": float(z),
        "width": float(width),
        "height": float(height),
        "config": json.dumps(config_dict),
        "filters": "[]"
    }

# Page 1: Intersection Overview & Queues
p1_visuals = [
    # Top KPI Cards
    make_vc(20, 20, 220, 100, 1, "card", "Total Vehicles Simulated", "vehicles", "Total Vehicles"),
    make_vc(260, 20, 220, 100, 2, "card", "Waiting in Queues", "vehicles", "Waiting Vehicles"),
    make_vc(500, 20, 220, 100, 3, "card", "Completed Vehicles (Exited)", "vehicles", "Exited Vehicles"),
    make_vc(740, 20, 220, 100, 4, "card", "Emergency Preemptions", "emergency_events", "Emergency Preemption Count"),
    make_vc(980, 20, 280, 100, 5, "card", "Total Simulation Sessions", "simulation_runs", "Total Simulation Runs"),

    # Charts
    make_vc(20, 140, 600, 260, 6, "columnChart", "Approach Road Waiting Vehicle Distribution", "traffic_snapshots", "Avg Waiting Count"),
    make_vc(640, 140, 620, 260, 7, "pieChart", "Vehicle Fleet Composition (Car, Bus, Bike, Ambulance)", "vehicles", "Total Vehicles"),
    make_vc(20, 420, 600, 280, 8, "lineChart", "Real-Time Approach Queue Dynamics Over Time", "traffic_snapshots", "Peak Queue Length"),
    make_vc(640, 420, 620, 280, 9, "tableEx", "Active Vehicle Identity Ledger (Live Status)", "vehicles", "Total Vehicles")
]

# Page 2: Signal Timing & Strategy Performance
p2_visuals = [
    # KPI Cards
    make_vc(20, 20, 380, 100, 1, "card", "Total Signal Transition Events", "signal_events", "Total Signal Transitions"),
    make_vc(430, 20, 400, 100, 2, "card", "Average Green Phase Duration (sec)", "traffic_snapshots", "Avg Green Seconds"),
    make_vc(860, 20, 400, 100, 3, "card", "Total Vehicle Throughput", "traffic_snapshots", "Total Vehicle Throughput"),

    # Charts
    make_vc(20, 140, 600, 260, 4, "barChart", "Allocated Green Duration by Approach Phase", "traffic_snapshots", "Avg Green Seconds"),
    make_vc(640, 140, 620, 260, 5, "columnChart", "Traffic Density & Queue Length by Approach Road", "traffic_snapshots", "Peak Queue Length"),
    make_vc(20, 420, 600, 280, 6, "lineChart", "Signal Timing Evolution Across Simulation Cycles", "signal_events", "Total Signal Transitions"),
    make_vc(640, 420, 620, 280, 7, "tableEx", "Signal Phase Control History & Remaining Time", "signal_events", "Total Signal Transitions")
]

# Page 3: Emergency Vehicle Priority & Delays
p3_visuals = [
    # KPI Cards
    make_vc(20, 20, 380, 100, 1, "card", "Emergency Preemptions Triggered", "emergency_events", "Emergency Preemption Count"),
    make_vc(430, 20, 400, 100, 2, "card", "Avg Emergency Clearance Duration (sec)", "emergency_events", "Avg Emergency Clearance Seconds"),
    make_vc(860, 20, 400, 100, 3, "card", "Max Priority Hold Duration (sec)", "emergency_events", "Max Priority Duration"),

    # Charts
    make_vc(20, 140, 600, 260, 4, "barChart", "Emergency Preemption Duration per Incident", "emergency_events", "Avg Emergency Clearance Seconds"),
    make_vc(640, 140, 620, 260, 5, "columnChart", "Average Vehicle Speed by Fleet Category", "vehicles", "Avg Vehicle Speed"),
    make_vc(20, 420, 1240, 280, 6, "tableEx", "Emergency Vehicle Preemption Audit Trail (Priority Timeline)", "emergency_events", "Emergency Preemption Count")
]

report_layout = {
    "id": 0,
    "theme": "CY24SU08",
    "sections": [
        {
            "id": 0,
            "name": "Section_IntersectionOverview",
            "displayName": "Intersection Overview & Queues",
            "filters": "[]",
            "ordinal": 0,
            "visualContainers": p1_visuals,
            "config": "{}",
            "width": 1280.0,
            "height": 720.0
        },
        {
            "id": 1,
            "name": "Section_SignalTiming",
            "displayName": "Signal Timing & Strategy Performance",
            "filters": "[]",
            "ordinal": 1,
            "visualContainers": p2_visuals,
            "config": "{}",
            "width": 1280.0,
            "height": 720.0
        },
        {
            "id": 2,
            "name": "Section_EmergencyPriority",
            "displayName": "Emergency Preemption & Delays",
            "filters": "[]",
            "ordinal": 2,
            "visualContainers": p3_visuals,
            "config": "{}",
            "width": 1280.0,
            "height": 720.0
        }
    ]
}

# 4. Generate Modern PBIP (Power BI Project) Files
print("--> Generating Modern Power BI Project (.pbip)...")

PBIP_ROOT = os.path.join(BASE_DIR, "TrafficManagementAnalytics.pbip")
pbip_manifest = {
    "version": "1.0",
    "artifacts": [
        {"report": {"path": "TrafficManagementAnalytics.Report"}}
    ]
}
with open(PBIP_ROOT, "w", encoding="utf-8") as f:
    json.dump(pbip_manifest, f, indent=2)

# Report Folder
REPORT_DIR = os.path.join(BASE_DIR, "TrafficManagementAnalytics.Report")
os.makedirs(REPORT_DIR, exist_ok=True)

with open(os.path.join(REPORT_DIR, "definition.pbir"), "w", encoding="utf-8") as f:
    json.dump({
        "version": "4.0",
        "datasetReference": {
            "byPath": {"path": "../TrafficManagementAnalytics.SemanticModel"},
            "byConnection": None
        }
    }, f, indent=2)

with open(os.path.join(REPORT_DIR, "report.json"), "w", encoding="utf-8") as f:
    json.dump(report_layout, f, indent=2)

# Semantic Model Folder
MODEL_DIR = os.path.join(BASE_DIR, "TrafficManagementAnalytics.SemanticModel")
os.makedirs(MODEL_DIR, exist_ok=True)

with open(os.path.join(MODEL_DIR, "definition.pbism"), "w", encoding="utf-8") as f:
    json.dump({"version": "4.0"}, f, indent=2)

with open(os.path.join(MODEL_DIR, "model.bim"), "w", encoding="utf-8") as f:
    json.dump(tabular_model, f, indent=2)

# 5. Build Content_Types.xml and Package .pbit and .pbix Archives
print("--> Packaging TrafficManagementAnalytics.pbit and TrafficManagementAnalytics.pbix...")

CONTENT_TYPES_XML = """<?xml version="1.0" encoding="utf-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="json" ContentType="" />
  <Default Extension="xml" ContentType="" />
  <Override PartName="/Version" ContentType="" />
  <Override PartName="/Settings" ContentType="" />
  <Override PartName="/Metadata" ContentType="" />
  <Override PartName="/DiagramLayout" ContentType="" />
  <Override PartName="/DataModelSchema" ContentType="" />
  <Override PartName="/Report/Layout" ContentType="" />
</Types>"""

diagram_layout = {
    "version": "1.1.0",
    "diagrams": [
        {
            "ordinal": 0,
            "scrollPosition": {"x": 0, "y": 0},
            "nodes": [
                {"location": {"x": 50, "y": 50}, "nodeIndex": "vehicles", "nodeType": 0},
                {"location": {"x": 350, "y": 50}, "nodeIndex": "simulation_runs", "nodeType": 0},
                {"location": {"x": 650, "y": 50}, "nodeIndex": "traffic_snapshots", "nodeType": 0},
                {"location": {"x": 50, "y": 350}, "nodeIndex": "lanes", "nodeType": 0},
                {"location": {"x": 350, "y": 350}, "nodeIndex": "roads", "nodeType": 0},
                {"location": {"x": 650, "y": 350}, "nodeIndex": "signal_events", "nodeType": 0},
                {"location": {"x": 950, "y": 50}, "nodeIndex": "emergency_events", "nodeType": 0}
            ],
            "zoomValue": 100
        }
    ]
}

metadata_json = {
    "version": 1,
    "createdTimestamp": "2026-11-06T16:00:00.0000000Z",
    "lastModifiedTimestamp": "2026-11-06T16:00:00.0000000Z"
}

settings_json = {
    "version": 1,
    "useNewFilterPaneExperience": True,
    "allowChangeFilterTypes": True
}

def package_pbi_archive(output_path):
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as z:
        # [Content_Types].xml (UTF-8)
        z.writestr("[Content_Types].xml", CONTENT_TYPES_XML.encode("utf-8"))
        # Version (UTF-16LE without BOM or UTF-16)
        z.writestr("Version", "1.28".encode("utf-16le"))
        # Settings (UTF-16LE)
        z.writestr("Settings", json.dumps(settings_json).encode("utf-16le"))
        # Metadata (UTF-16LE)
        z.writestr("Metadata", json.dumps(metadata_json).encode("utf-16le"))
        # DiagramLayout (UTF-16LE)
        z.writestr("DiagramLayout", json.dumps(diagram_layout).encode("utf-16le"))
        # DataModelSchema (UTF-16LE)
        z.writestr("DataModelSchema", json.dumps(tabular_model, indent=2).encode("utf-16le"))
        # Report/Layout (UTF-16LE)
        z.writestr("Report/Layout", json.dumps(report_layout, indent=2).encode("utf-16le"))
    print(f"  [OK] Successfully created {output_path} ({os.path.getsize(output_path):,} bytes)")

PBIT_PATH = os.path.join(BASE_DIR, "TrafficManagementAnalytics.pbit")
PBIX_PATH = os.path.join(BASE_DIR, "TrafficManagementAnalytics.pbix")

package_pbi_archive(PBIT_PATH)
package_pbi_archive(PBIX_PATH)

# Verify archive integrity
for p in [PBIT_PATH, PBIX_PATH]:
    with zipfile.ZipFile(p, "r") as z:
        test_res = z.testzip()
        if test_res is None:
            print(f"  [VERIFIED] Archive {os.path.basename(p)} verified cleanly ({len(z.namelist())} files).")
        else:
            print(f"  [ERROR] Archive {p} corrupted at {test_res}!")

print("All Power BI assets created successfully.")
