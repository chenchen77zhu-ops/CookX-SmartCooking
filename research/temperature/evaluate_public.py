"""External risk benchmark, held out entirely from training and threshold tuning."""
import json, math, subprocess
from pathlib import Path
import openpyxl
ROOT=Path(__file__).resolve().parents[2]
path=ROOT/"tmp/nist/original.xlsx"
book=openpyxl.load_workbook(path,read_only=True,data_only=True)
runs={}
for row in book["data"].iter_rows(min_row=3,values_only=True):
    if not isinstance(row[0],(int,float)):continue
    exp=int(row[0]);time=row[1];temp=row[4];label=row[3]
    if not isinstance(time,(int,float)):continue
    if not isinstance(temp,(int,float)) or not math.isfinite(temp):temp=None
    runs.setdefault(exp,{"id":exp,"rows":[]})["rows"].append({"time":time,"temp":temp,"preIgnition":label})
for run in runs.values():run["rows"].sort(key=lambda r:r["time"])
proc=subprocess.run(["node",str(Path(__file__).with_name("external-risk.mjs"))],input=json.dumps(list(runs.values())),capture_output=True,text=True,encoding="utf-8",check=True)
report=json.loads(proc.stdout)
report.update(source="https://doi.org/10.18434/M32171",temperatureColumn="Pan TC center",labelColumn="Pre-Ignition",
              trainingUse=False,thresholdTuningOnThisDataset=False,
              limitation="Temperature-only auxiliary alarms; experiment labels depend on cooking conditions and sometimes hottest-location estimates. Thermocouple sampling at 4 s and 2.2 C manufacturer uncertainty differ from MLX90614. Out-of-range/missing data count as abstentions and missed alarms, never clipped. No model stage F1: dataset has no compatible six-phase labels. Models do not override rule risk.",
              metricsDefinition="Positive-sample recall counts abstentions as missed positives. Event recall: any alarm within each run's labelled pre-ignition interval; delay: first alarm in that interval minus its start; false episodes: alarm rising edges in labelled normal conditions, including early alerts.")
(ROOT/"docs/temperature/public-risk-results.json").write_text(json.dumps(report,indent=2,allow_nan=False),encoding="utf-8")
print(json.dumps(report,indent=2))
