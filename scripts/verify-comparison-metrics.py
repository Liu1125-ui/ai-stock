"""Independent Decimal audit of Java comparison output, using saved evidence only."""
import hashlib
import json
import sys
from decimal import Decimal, localcontext
from pathlib import Path


def main():
    fixture, output = map(Path, sys.argv[1:3])
    sources = [json.loads((fixture / f"evidence-{stock}.json").read_text(encoding="utf-8-sig"))
               for stock in ("SH600030", "SH600276", "SZ000002")]
    digest = hashlib.sha256(json.dumps(sorted(sources, key=lambda e: e["instrumentId"]),
                                      separators=(",", ":"), ensure_ascii=False).encode()).hexdigest()
    report = []
    with localcontext() as context:
        context.prec = 50
        for period in ("3M", "6M", "1Y"):
            actual = json.loads((output / f"calculation-{period}.json").read_text(encoding="utf-8"))
            assert actual["sourceHash"] == digest
            start, end = (actual["requestedRange"][key] for key in ("from", "to"))
            days = [d["date"] for d in sources[0]["calendar"] if d["open"] and start <= d["date"] <= end]
            maps = [{d["date"]: d for d in source["days"]} for source in sources]
            common = [d for d in days if all(m[d]["priceState"] == "VERIFIED" for m in maps)]
            assert len(common) >= 2
            dates = [d for d in days if common[0] <= d <= common[-1]]
            assert actual["actualRange"] == {"from": common[0], "to": common[-1]}
            for source, rows, stock in zip(sources, maps, actual["stocks"]):
                assert stock["instrumentId"] == source["instrumentId"]
                assert all(rows[d]["priceState"] == rows[d]["volumeState"] == "VERIFIED" for d in dates)
                prices = [Decimal(rows[d]["backwardClose"]) for d in dates]
                volumes = [Decimal(rows[d]["volumeShares"]) for d in dates]
                returns = [b / a - 1 for a, b in zip(prices, prices[1:])]
                mean = sum(returns) / len(returns)
                variance = sum((r - mean) ** 2 for r in returns) / (len(returns) - 1)
                peak, drawdown = prices[0], Decimal(0)
                for price in prices:
                    peak = max(peak, price)
                    drawdown = max(drawdown, 1 - price / peak)
                expected = {"intervalReturn": (prices[-1] / prices[0] - 1) * 100,
                            "annualizedVolatility": (variance * 252).sqrt() * 100,
                            "maxDrawdown": drawdown * 100,
                            "meanVolume": sum(volumes) / len(volumes)}
                for key, value in expected.items():
                    metric = stock[key]
                    assert metric["status"] == "AVAILABLE"
                    error = abs(Decimal(metric["value"]) - value)
                    tolerance = Decimal("0.00005" if key == "meanVolume" else "0.0000005")
                    assert error <= tolerance, (period, source["instrumentId"], key, str(error))
                    report.append({"period": period, "instrumentId": source["instrumentId"],
                                   "metric": key, "value": metric["value"], "absoluteError": str(error),
                                   "tolerance": str(tolerance), "observations": metric["observationCount"]})
                assert len(stock["chart"]) == len(dates)
                for day, price, point in zip(dates, prices, stock["chart"]):
                    assert point["date"] == day
                    assert abs(Decimal(point["normalizedClose"]) - price / prices[0] * 100) <= Decimal("0.0000005")
    result = {"passed": True, "metricChecks": len(report), "sourceHash": digest, "checks": report}
    (output / "independent-metrics-audit.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Independent Decimal audit passed: {len(report)} metric comparisons; 3 stocks, 3 periods.")


if __name__ == "__main__":
    main()
