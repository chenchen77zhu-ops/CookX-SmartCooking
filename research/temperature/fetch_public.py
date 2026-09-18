"""Fetch NIST's original file for descriptive inspection only; never pan training."""
import hashlib, json, urllib.request, zipfile
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
target=ROOT/"tmp/nist"
target.mkdir(parents=True,exist_ok=True)
url="https://data.nist.gov/od/ds/mds2-2171/all%20data4-10-20.xlsx"
report={"source":"NIST M32171","url":url,"used_for_pan_training":False}
try:
    data=urllib.request.urlopen(url,timeout=60).read()
    (target/"original.xlsx").write_bytes(data)
    report.update(downloaded=True,bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
    with zipfile.ZipFile(target/"original.xlsx") as z:
        ns={"m":"http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
        workbook=ET.fromstring(z.read("xl/workbook.xml"))
        report["sheets"]=[s.attrib["name"] for s in workbook.findall("m:sheets/m:sheet",ns)]
        texts=ET.fromstring(z.read("xl/sharedStrings.xml"))
        strings=["".join(n.itertext()) for n in texts.findall("m:si",ns)]
        report["temperature_column_descriptions"]=[s for s in strings if "temp" in s.lower() or "thermo" in s.lower()][:35]
        report["limitations"]="Includes pan Type-K thermocouples as well as duct sensors; 4-second sampling. Not MLX90614 observations. External risk benchmark only; excluded from training."
except Exception as e:
    report.update(downloaded=False,error=str(e))
(ROOT/"docs/temperature/public-data-inspection.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
print(json.dumps(report,indent=2))
