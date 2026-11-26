from unittest import TestCase
from unittest.mock import patch

from app.inference.pool_artifacts import assemble_pool


class PoolArtifactTest(TestCase):
    def test_pool_is_order_independent_and_rejects_duplicate_stocks(self):
        parent={"checkpointHash":"b"*64,"instruments":["SH600030","SH600276","SZ000002"]}
        def summary(report, *_):
            return {"instrumentId":report["instrumentId"]}
        first=b'{"instrumentId":"SH601633"}'
        second=b'{"instrumentId":"SZ002304"}'
        with patch("app.inference.pool_artifacts.summarize",side_effect=summary):
            left=assemble_pool("parent","a"*64,parent,[first,second])
            right=assemble_pool("parent","a"*64,parent,[second,first])
            self.assertEqual(left,right)
            self.assertEqual(left["instruments"],["SH601633","SZ002304"])
            self.assertEqual(left["checkpointHash"],parent["checkpointHash"])
            with self.assertRaises(ValueError):
                assemble_pool("parent","a"*64,parent,[first,first])
