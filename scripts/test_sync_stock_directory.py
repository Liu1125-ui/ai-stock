"""Regression tests for the metadata-only stock directory sync."""

from datetime import datetime, timezone
import unittest

from sync_stock_directory import directory_rows, import_sql


class StockDirectoryTest(unittest.TestCase):
    def test_filters_non_a_shares_and_inactive_securities(self):
        rows = [
            ("sh.600519", "贵州茅台", "1", "1"),
            ("sh.688981", "中芯国际", "1", "1"),
            ("sz.300750", "宁德时代", "1", "1"),
            ("sh.900901", "沪市B股", "1", "1"),
            ("sz.200011", "深市B股", "1", "1"),
            ("sz.000001", "平安银行", "1", "0"),
            ("sh.000001", "上证指数", "2", "1"),
            ("sz.159915", "基金", "5", "1"),
        ]
        self.assertEqual(list(directory_rows(rows)), ["SH600519", "SH688981", "SZ300750"])

    def test_rejects_empty_and_conflicting_source(self):
        with self.assertRaises(ValueError):
            directory_rows([])
        with self.assertRaises(ValueError):
            directory_rows([("sh.600519", "a", "1", "1"), ("sh.600519", "b", "1", "1")])

    def test_sql_changes_only_metadata_and_encodes_names(self):
        sql = import_sql({"SH600519": "茅台'股份"}, datetime(2026, 11, 19, tzinfo=timezone.utc))
        self.assertIn("START TRANSACTION;", sql)
        self.assertIn("INSERT INTO instrument", sql)
        self.assertIn("ON DUPLICATE KEY UPDATE instrument_id=instrument.instrument_id", sql)
        self.assertIn("0x" + "茅台'股份".encode().hex(), sql)
        self.assertNotIn("茅台'股份", sql)
        self.assertNotIn("market_data_version", sql)
        self.assertTrue(sql.endswith("COMMIT;\n"))


if __name__ == "__main__":
    unittest.main()
