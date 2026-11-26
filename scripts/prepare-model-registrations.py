"""Prepare immutable candidate registrations from original local research JSON; no training."""
import argparse
import hashlib
import json
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path


def metric(value, maximum):
    try:
        number = Decimal(str(value))
        if not number.is_finite() or number < 0 or number > maximum:
            raise ValueError("metric outside supported range")
        return format(number.quantize(Decimal("0.000000000001"), rounding=ROUND_HALF_UP), "f")
    except InvalidOperation as exc:
        raise ValueError("invalid metric") from exc


def registration(artifact, report, source_bytes):
    if (report.get("seed") != artifact["seed"] or report.get("cutoff") != "2023-12-31"
            or report.get("mode") != "absolute" or report.get("window") != "one_year"):
        raise ValueError("report provenance does not match this candidate")
    policy = report["policies"]["rmse6p2"]
    if policy["epoch"] != artifact["epoch"]:
        raise ValueError("checkpoint epoch does not match the evaluation policy")
    evaluation = {
        "sourceHash": hashlib.sha256(source_bytes).hexdigest(),
        "scope": "RETROSPECTIVE_DEVELOPMENT", "seed": artifact["seed"], "validationYear": 2024,
        "validationMae": metric(policy["validation"]["mae"], 10),
        "validationBaselineMae": metric(policy["validation_baseline"]["mae"], 10),
        "mae2025": metric(policy["evaluation"]["year2025"]["mae"], 10),
        "mae2026": metric(policy["evaluation"]["year2026"]["mae"], 10),
        "direction2026": metric(policy["evaluation"]["year2026"]["direction"], 1),
    }
    if Decimal(evaluation["validationBaselineMae"]) == 0:
        raise ValueError("baseline must be nonzero")
    return {"artifactId": artifact["artifactId"], "manifestHash": artifact["manifestHash"], "evaluation": evaluation}


def main():
    root = Path(__file__).resolve().parents[1]
    stock_root = root.parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, default=root / "runtime/model-artifacts/catalog.json")
    parser.add_argument("--reports", type=Path, default=stock_root / "实验与测试/联合研究/顺序改进验证/结果")
    parser.add_argument("--output", type=Path, default=root / "runtime/sdd005c/candidates")
    args = parser.parse_args()
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    artifacts = catalog["artifacts"]
    if sorted(a["seed"] for a in artifacts) != [42, 43, 44]:
        raise ValueError("expected exactly the three sealed candidates")
    prepared = []
    for artifact in artifacts:
        path = args.reports / f"S3_one_year_{artifact['seed']}.json"
        source = path.read_bytes()
        item = registration(artifact, json.loads(source), source)
        prepared.append((artifact["seed"], item))
    args.output.mkdir(parents=True, exist_ok=True)
    for seed, item in prepared:
        (args.output / f"registration-{seed}.json").write_text(json.dumps(item, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Prepared seed {seed}: {item['artifactId']} (retrospective development evidence)")


if __name__ == "__main__":
    main()
