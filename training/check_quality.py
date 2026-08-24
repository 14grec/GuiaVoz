#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


parser = argparse.ArgumentParser()
parser.add_argument("--report", type=Path, default=Path(__file__).with_name("training_report.json"))
parser.add_argument("--minimum", type=float, default=0.90)
arguments = parser.parse_args()
report = json.loads(arguments.report.read_text(encoding="utf-8"))
accuracy = float(report["accuracy"])
critical = float(report.get("critical_whatsapp_accuracy", 0.0))
if accuracy < arguments.minimum:
    raise SystemExit(f"Acurácia {accuracy:.2%} abaixo do mínimo {arguments.minimum:.2%}.")
if critical < arguments.minimum:
    raise SystemExit(f"Acurácia crítica do WhatsApp {critical:.2%} abaixo do mínimo {arguments.minimum:.2%}.")
print(f"Qualidade neural aprovada: {accuracy:.2%} geral e {critical:.2%} nos comandos críticos "
      f"em {report['test_examples']} comandos inéditos.")
