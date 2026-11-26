"""Read-only independent Decimal audit of one frozen five-day evaluation.
No Java calculator, inference, provider, mutation, or credential export is used.
"""
from __future__ import annotations
import argparse
import copy
import datetime as dt
from decimal import Decimal as D, localcontext, ROUND_HALF_UP
import hashlib
import json
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
EPS = D("1e-15")

class AuditError(RuntimeError):
    pass

def require(condition, label):
    if not condition:
        raise AuditError(label)

def sql(role, statement):
    require(role in {"prediction", "market", "scheduler"}, "Unsupported audit role")
    require(statement.lstrip().lower().startswith("select "), "Read-only SELECT required")
    shell = 'MYSQL_PWD="$' + role.upper() + '_DB_PASSWORD" mysql -u stock_' + role + ' stock_' + role + ' --batch --raw --skip-column-names'
    result = subprocess.run(["docker", "exec", "-i", "aistock-sdd001-mysql-1", "sh", "-c", shell], input=statement, capture_output=True, text=True, encoding="utf-8", timeout=30)
    require(result.returncode == 0, "Audit database SELECT failed; private output withheld")
    return result.stdout.strip()

def parse_json(role, statement):
    return json.loads(sql(role, statement))

def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()

def calculate(snapshot, evidence):
    """Direct mathematical reconstruction, with 80-digit intermediate precision."""
    with localcontext() as ctx:
        ctx.prec = 80
        fixed = snapshot["input"]
        forecast = snapshot["prediction"]
        dates = fixed["targetDates"]
        require(len(dates) == 5 and len(set(dates)) == 5 and len(forecast["points"]) == 5, "Five unique target dates and forecast points required")
        require(dates == evidence["targetDates"] == sorted(dates), "Target dates differ")
        require(fixed["originDate"] == evidence["originDate"] == forecast["originDate"], "Origin dates differ")
        require(fixed["instrumentId"] == evidence["instrumentId"] == forecast["instrumentId"], "Instrument differs")
        require(forecast["formalInference"] and not forecast["validationOnly"], "Not a formal saved forecast")
        require(forecast["referenceBasis"] == "ORIGIN_SHARE_BASIS", "Wrong price basis")
        require(evidence["provider"] == "baostock" and evidence["definition"] == "baostock-backward-close-over-raw-v1", "Unsupported source definition")
        require(dt.date.fromisoformat(evidence["dueThrough"]) >= dt.date.fromisoformat(dates[-1]), "Five targets not all due")
        origin = D(fixed["originRawClose"])
        require(origin == D(forecast["originRawClose"]) and origin > 0, "Frozen origin invalid")
        observations = {day["date"]: day for day in evidence["observations"]}
        require(len(observations) == len(evidence["observations"]) == 5 and set(observations) == set(dates), "Observations incomplete or duplicate")
        scale = D(evidence["originBackwardClose"]) / D(evidence["originRawClose"])
        require(scale > 0, "Invalid origin adjustment scale")
        rows = []
        for i, date in enumerate(dates):
            original = forecast["points"][i]
            require(original["step"] == i + 1 and original["targetDate"] == date, "Forecast step mismatch")
            observed = observations[date]
            require(observed["state"] == "VERIFIED", "Target not actually verified")
            predicted = D(original["referencePrice"]).quantize(D("0.000001"), rounding=ROUND_HALF_UP)
            actual = (D(observed["backwardClose"]) / scale).quantize(D("0.000001"), rounding=ROUND_HALF_UP)
            signed = predicted - actual
            sign = lambda value: (value > 0) - (value < 0)
            rows.append({"step": i+1, "targetDate": date, "state": "VERIFIED", "referencePrice": str(predicted), "actualPrice": str(actual), "signedError": str(signed), "absoluteError": str(abs(signed)), "normalizedAbsoluteError": str(abs(signed)/origin), "predictedDirection": sign(predicted-origin), "actualDirection": sign(actual-origin), "directionHit": sign(predicted-origin) == sign(actual-origin), "baselineNormalizedAbsoluteError": str(abs(origin-actual)/origin)})
        errors = [D(row["signedError"]) for row in rows]
        baselines = [origin-D(row["actualPrice"]) for row in rows]
        mean = lambda values: sum(values, D(0)) / D(5)
        mae = mean([abs(error) for error in errors])
        rmse = mean([error*error for error in errors]).sqrt()
        baseline_mae = mean([abs(error) for error in baselines])
        baseline_rmse = mean([error*error for error in baselines]).sqrt()
        metrics = {"priceMae": mae, "priceRmse": rmse, "ratioMae": mae/origin, "ratioRmse": rmse/origin, "directionAccuracy": D(sum(row["directionHit"] for row in rows))/5, "baselineRatioMae": baseline_mae/origin, "baselineRatioRmse": baseline_rmse/origin, "relativeMaeImprovement": None if baseline_mae == 0 else (baseline_mae-mae)/baseline_mae}
        return rows, metrics, {"baselinePriceMae": str(baseline_mae), "baselinePriceRmse": str(baseline_rmse), "directionHits": sum(row["directionHit"] for row in rows), "validDenominator": 5}

def compare(saved, points, metrics, origin_revised):
    require(saved["state"] == "COMPLETE" and saved["dueCount"] == saved["verifiedCount"] == 5 and saved["missingCount"] == 0, "Saved status/count mismatch")
    require(saved["originRawRevised"] == origin_revised, "Origin revision flag mismatch")
    require(len(saved["points"]) == 5, "Saved point count mismatch")
    checks = 0
    numeric = {"referencePrice", "actualPrice", "signedError", "absoluteError", "normalizedAbsoluteError", "baselineNormalizedAbsoluteError"}
    for got, expected in zip(saved["points"], points):
        for key, value in expected.items():
            if key in numeric:
                require(got.get(key) is not None and abs(D(got[key])-D(value)) <= EPS, "Point field mismatch: " + key)
            else:
                require(got.get(key) == value, "Point field mismatch: " + key)
            checks += 1
    require(set(saved["metrics"]) == set(metrics), "Metric schema mismatch")
    for key, expected in metrics.items():
        actual = saved["metrics"][key]
        require(actual is None if expected is None else actual is not None and abs(D(actual)-expected) <= EPS, "Aggregate metric mismatch: " + key)
        checks += 1
    return checks

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prediction-id", required=True)
    parser.add_argument("--output", required=True, help="New JSON evidence file; existing outputs are not overwritten")
    args = parser.parse_args()
    pid = str(uuid.UUID(args.prediction_id))
    output = Path(args.output).resolve()
    require(output.is_relative_to(ROOT) and output.suffix == ".json" and not output.exists(), "Output must be a new project-local JSON file")
    frozen = parse_json("prediction", f"SELECT JSON_OBJECT('snapshot',CAST(snapshot_json AS JSON),'snapshotSqlHash',SHA2(snapshot_json,256),'model',CAST(model_json AS JSON),'state',state) FROM forecast_request WHERE prediction_id='{pid}'")
    require(frozen["state"] == "SUCCEEDED", "Saved forecast not successful")
    version = str(uuid.UUID(sql("prediction", f"SELECT current_version_id FROM forecast_verification_head WHERE prediction_id='{pid}'")))
    record = parse_json("prediction", f"SELECT JSON_OBJECT('evaluation',CAST(evaluation_json AS JSON),'evidence',CAST(evidence_json AS JSON),'evidenceId',realization_version_id,'rule',rule_version,'hash',content_hash) FROM forecast_verification_version WHERE version_id='{version}' AND prediction_id='{pid}'")
    snapshot, evidence, saved = frozen["snapshot"], record["evidence"], record["evaluation"]
    require(record["rule"] == saved["ruleVersion"] == "origin-share-close-v1", "Rule differs")
    eid = str(uuid.UUID(evidence["realizationVersionId"]))
    require(eid == record["evidenceId"] == saved["evidenceVersionId"], "Evidence reference differs")
    source = parse_json("market", f"SELECT evidence_json FROM realization_version WHERE version_id='{eid}'")
    require(source == evidence, "Saved market/prediction evidence differ")
    raw_id = str(uuid.UUID(evidence["rawVersionId"]))
    raw_meta = parse_json("market", f"SELECT JSON_OBJECT('hash',content_hash,'provider',provider,'basis',price_basis,'instrument',instrument_id) FROM market_data_version WHERE version_id='{raw_id}'")
    require(raw_meta == {"hash":evidence["rawContentHash"],"provider":"baostock","basis":"RAW","instrument":evidence["instrumentId"]}, "Frozen RAW metadata mismatch")
    dates = [evidence["originDate"], *evidence["targetDates"]]
    date_sql = ','.join("'"+dt.date.fromisoformat(day).isoformat()+"'" for day in dates)
    raw = {line.split("\t")[0]:D(line.split("\t")[1]) for line in sql("market", f"SELECT trade_date,close_price FROM market_daily_bar WHERE version_id='{raw_id}' AND trade_date IN ({date_sql})").splitlines()}
    require(len(raw) == 6 and raw[evidence["originDate"]] == D(evidence["originRawClose"]), "RAW origin/date missing")
    for observation in evidence["observations"]:
        require(raw.get(observation["date"]) == D(observation["rawClose"]), "Target RAW close mismatch")
    calendar = {day["date"]:day["open"] for day in evidence["calendar"]}
    require(all(calendar.get(day) is True for day in dates), "Target not a saved open trading day")
    first_five = sorted(day for day, opened in calendar.items() if opened is True and day > evidence["originDate"])[:5]
    require(first_five == evidence["targetDates"], "Targets are not the first five saved trading days")
    points, metrics, extra = calculate(snapshot, evidence)
    revised = D(evidence["originRawClose"]) != D(snapshot["input"]["originRawClose"])
    checks = compare(saved, points, metrics, revised)
    controls = []
    for name in ("signedError", "directionHit", "metric", "denominator"):
        changed = copy.deepcopy(saved)
        if name == "signedError": changed["points"][0]["signedError"] = str(D(changed["points"][0]["signedError"])+1)
        elif name == "directionHit": changed["points"][0]["directionHit"] = not changed["points"][0]["directionHit"]
        elif name == "metric": changed["metrics"]["priceMae"] = "999"
        else: changed["verifiedCount"] = 4
        try: compare(changed, points, metrics, revised)
        except AuditError: controls.append(name)
        else: raise AuditError("Negative control not detected: "+name)
    unchanged = sql("prediction", f"SELECT SHA2(snapshot_json,256) FROM forecast_request WHERE prediction_id='{pid}'") == frozen["snapshotSqlHash"]
    require(unchanged, "Original forecast changed during audit")
    old_baseline = ROOT / "runtime/sdd005d3b2-before-state.json"
    historical_match = None
    if old_baseline.exists():
        historical = json.loads(old_baseline.read_text(encoding="utf-8"))
        historical_hash = historical["prediction"].split("\t")[-1].strip()
        historical_match = historical_hash.lower() == frozen["snapshotSqlHash"].lower()
        require(historical_match, "Original forecast differs from recorded Oct5 snapshot hash")
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {"checkedAt":dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat(),"predictionId":pid,"verificationVersionId":version,"evidenceVersionId":eid,"rawVersionId":raw_id,"originDate":evidence["originDate"],"originPrice":snapshot["input"]["originRawClose"],"evidenceThrough":evidence["dueThrough"],"state":"COMPLETE","points":points,"metrics":{key:None if value is None else str(value) for key,value in metrics.items()},"extra":extra,"fieldChecks":checks,"negativeControlsDetected":controls,"sourceEvidenceMatchesMarket":True,"rawSixRowsMatch":True,"savedCalendarTargetsOpen":True,"targetsAreFirstFiveSavedTradingDays":True,"originalForecastMatchesOct5RecordedHash":historical_match,"originalForecastUnchanged":unchanged,"originalSnapshotSqlSha256":frozen["snapshotSqlHash"],"savedEvaluationSha256":digest(saved),"tolerance":"1e-15","numericMethod":"Python Decimal80 digits, independent direct formulas; actual prices HALF_UP6; exact flat direction after6-digit prices","externalCalls":0,"databaseWrites":0,"scope":"one frozen five-day stock forecast, not general model accuracy","browserAcceptance":"pending"}
    output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({"status":"PASS","checks":checks,"negativeControls":len(controls),"priceMae":str(metrics["priceMae"]),"directionHits":extra["directionHits"],"denominator":5,"output":str(output)},ensure_ascii=False))

if __name__ == "__main__":
    try: main()
    except AuditError as error: raise SystemExit("Audit stopped: "+str(error))
