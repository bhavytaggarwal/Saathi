"""Tests for the backend's access checks and limits. Run: python -m unittest test_main"""
import os
import unittest
from unittest import mock

os.environ.setdefault("GOOGLE_CLOUD_PROJECT", "test-project")

import main  # noqa: E402
from fastapi import HTTPException  # noqa: E402


class FakeDoc:
    def __init__(self, store, key):
        self.store, self.key = store, key

    def set(self, data, merge=True):
        self.store[self.key] = self.store.get(self.key, 0) + 1

    def get(self):
        count = self.store.get(self.key, 0)
        return mock.Mock(to_dict=lambda: {"count": count})


class FakeFirestore:
    def __init__(self):
        self.counts = {}

    def collection(self, name):
        return mock.Mock(document=lambda doc_id: FakeDoc(self.counts, f"{name}/{doc_id}"))


def fake_request(ip="1.2.3.4"):
    return mock.Mock(headers={"x-forwarded-for": f"{ip}, 10.0.0.1"}, client=None)


class LimitsTest(unittest.TestCase):
    def setUp(self):
        self.db = FakeFirestore()
        self.patches = [
            mock.patch.object(main, "get_firestore_client", return_value=self.db),
            mock.patch.dict(os.environ, {"SAATHI_CLIENT_SECRET": "client-s", "EVAL_SECRET": "eval-s"}),
            mock.patch.object(main, "GLOBAL_DAILY_CAP", 5),
            mock.patch.object(main, "IP_BURST_PER_MINUTE", 3),
        ]
        for p in self.patches:
            p.start()
        main._ip_hits.clear()

    def tearDown(self):
        for p in self.patches:
            p.stop()

    def test_eval_bot_device_id_no_longer_skips_the_quota(self):
        with mock.patch.object(main, "DAILY_DEVICE_QUOTA", 2):
            main.enforce_daily_quota("eval_bot")
            main.enforce_daily_quota("eval_bot")
            with self.assertRaises(HTTPException) as err:
                main.enforce_daily_quota("eval_bot")
        self.assertEqual(429, err.exception.status_code)

    def test_eval_secret_skips_device_and_burst_limits_but_counts_globally(self):
        for _ in range(4):
            self.assertTrue(main.authenticate(fake_request(), "eval_bot", None, "eval-s"))
        self.assertFalse(any(k.startswith("daily_usage/") for k in self.db.counts))
        self.assertEqual(4, sum(v for k, v in self.db.counts.items() if k.startswith("global_usage/")))

    def test_wrong_secrets_are_refused(self):
        for client, ev in [(None, None), ("old-secret", None), (None, "wrong"), ("eval-s", None)]:
            with self.assertRaises(HTTPException) as err:
                main.authenticate(fake_request(), "device-123", client, ev)
            self.assertEqual(401, err.exception.status_code)

    def test_empty_eval_secret_on_the_server_never_matches(self):
        with mock.patch.dict(os.environ, {"EVAL_SECRET": ""}):
            self.assertFalse(main.is_eval_caller(""))
            self.assertFalse(main.is_eval_caller("anything"))

    def test_ip_burst_is_per_minute_not_per_day(self):
        for _ in range(3):
            main.enforce_ip_burst("5.5.5.5", now=100.0)
        with self.assertRaises(HTTPException) as err:
            main.enforce_ip_burst("5.5.5.5", now=110.0)
        self.assertEqual(429, err.exception.status_code)
        # Another phone behind a different IP is unaffected, and a minute later this one is fine.
        main.enforce_ip_burst("6.6.6.6", now=110.0)
        main.enforce_ip_burst("5.5.5.5", now=161.0)

    def test_burst_uses_the_first_forwarded_address(self):
        self.assertEqual("9.9.9.9", main.client_ip(fake_request("9.9.9.9")))

    def test_global_cap_stops_all_devices(self):
        for i in range(5):
            main.authenticate(fake_request(f"7.7.7.{i}"), f"device-{i:04d}", "client-s", None)
        with self.assertRaises(HTTPException) as err:
            main.authenticate(fake_request("8.8.8.8"), "device-new1", "client-s", None)
        self.assertEqual(429, err.exception.status_code)

    def test_reports_are_not_counted_against_the_caps(self):
        main.authenticate(fake_request(), "device-123", "client-s", None, count_usage=False)
        self.assertEqual({}, self.db.counts)


class NoRawTextInLogsTest(unittest.TestCase):
    def test_source_never_logs_prompt_or_reply_text(self):
        source = open(main.__file__, encoding="utf-8").read()
        for field in ("raw_prompt", "raw_response", "last_utterance=payload", "last_reply=payload"):
            # Firestore keeps the flagged words as dict keys ("last_utterance": ...), never as log fields.
            self.assertNotIn(field, source.replace('"last_utterance": payload', "").replace('"last_reply": payload', ""))


if __name__ == "__main__":
    unittest.main()
