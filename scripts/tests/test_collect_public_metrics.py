from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
import urllib.error
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from scripts.collect_public_metrics import (  # noqa: E402
    GitHubClient,
    MAX_RESPONSE_BYTES,
    MetricsError,
    _paginate,
    collect_metrics,
    render_metrics,
    write_metrics,
)


AS_OF = datetime(2026, 8, 12, 8, 0, tzinfo=timezone.utc)


class FakeClient:
    def __init__(self, responses):
        self.responses = responses
        self.calls = []

    def get_json(self, path, params=None):
        params = params or {}
        self.calls.append((path, params))
        key = (path, tuple(sorted((key, str(value)) for key, value in params.items())))
        if key in self.responses:
            return self.responses[key]
        fallback = (path, ())
        if fallback in self.responses:
            return self.responses[fallback]
        raise AssertionError(f"unexpected request: {path} {params}")


def paged(path, items, *, params=None, item_key=None):
    request_params = dict(params or {})
    request_params.update({"page": "1", "per_page": "100"})
    response = {item_key: items, "total_count": len(items)} if item_key else items
    return {(path, tuple(sorted(request_params.items()))): response}


def fixture_client():
    repo = "/repos/mxx1111/clinical-ai-safety-kit"
    responses = {
        (repo, ()): {
            "stargazers_count": 7,
            "forks_count": 3,
            "subscribers_count": 2,
            "owner": {"login": "must-not-leak"},
        }
    }
    responses.update(
        paged(
            f"{repo}/releases",
            [
                {
                    "draft": False,
                    "published_at": "2026-08-10T00:00:00Z",
                    "tag_name": "v0.2.0",
                    "assets": [{"download_count": 4, "uploader": {"login": "private-in-output"}}],
                },
                {
                    "draft": True,
                    "published_at": "2026-08-11T00:00:00Z",
                    "tag_name": "draft",
                    "assets": [{"download_count": 99}],
                },
                {
                    "draft": False,
                    "published_at": "2026-08-13T00:00:00Z",
                    "tag_name": "future",
                    "assets": [],
                },
            ],
        )
    )
    responses.update(
        paged(
            f"{repo}/issues",
            [
                {"state": "open", "created_at": "2026-08-01T00:00:00Z", "title": "must-not-leak"},
                {"state": "closed", "created_at": "2026-08-02T00:00:00Z", "body": "must-not-leak"},
                {"state": "open", "created_at": "2026-08-03T00:00:00Z", "pull_request": {}},
                {"state": "open", "created_at": "2026-08-13T00:00:00Z"},
            ],
            params={"state": "all", "labels": "feedback"},
        )
    )
    responses.update(
        paged(
            f"{repo}/issues",
            [
                {"state": "open", "created_at": "2026-07-01T00:00:00Z"},
                {"state": "closed", "created_at": "2026-07-02T00:00:00Z"},
                {"state": "closed", "created_at": "2026-07-03T00:00:00Z"},
            ],
            params={"state": "all", "labels": "agent-task"},
        )
    )
    responses.update(
        paged(
            f"{repo}/pulls",
            [
                {"created_at": "2026-07-01T00:00:00Z", "merged_at": "2026-07-03T00:00:00Z", "user": {"login": "alice"}},
                {"created_at": "2026-08-01T00:00:00Z", "merged_at": None, "user": {"login": "bob"}},
                {"created_at": "2026-08-13T00:00:00Z", "merged_at": None},
            ],
            params={"state": "all"},
        )
    )
    responses.update(
        paged(
            f"{repo}/actions/runs",
            [
                {"created_at": "2026-08-01T00:00:00Z", "status": "completed", "conclusion": "success", "actor": {"login": "carol"}},
                {"created_at": "2026-08-02T00:00:00Z", "status": "completed", "conclusion": "failure"},
                {"created_at": "2026-08-03T00:00:00Z", "status": "in_progress", "conclusion": None},
                {"created_at": "2026-07-01T00:00:00Z", "status": "completed", "conclusion": "success"},
                {"created_at": "2026-08-13T00:00:00Z", "status": "completed", "conclusion": "success"},
            ],
            params={"created": "2026-07-13T08:00:00Z..2026-08-12T08:00:00Z"},
            item_key="workflow_runs",
        )
    )
    return FakeClient(responses)


class CollectPublicMetricsTest(unittest.TestCase):
    def test_collects_only_aggregate_counts_with_exact_formulas(self):
        metrics = collect_metrics(fixture_client(), "mxx1111/clinical-ai-safety-kit", AS_OF)

        self.assertEqual(metrics["reach"], {"stars": 7, "forks": 3, "subscribers": 2})
        self.assertEqual(
            metrics["activation"],
            {
                "publishedReleases": 1,
                "releaseAssetDownloads": 4,
                "firstUserFeedbackSubmissions": 2,
            },
        )
        self.assertEqual(metrics["feedback"], {"open": 1, "closed": 1, "total": 2})
        self.assertEqual(
            metrics["contribution"],
            {
                "pullRequestsOpened": 2,
                "pullRequestsMerged": 1,
                "pullRequestMergeRatePercent": 50.0,
                "agentTasksOpen": 1,
                "agentTasksClosed": 2,
            },
        )
        self.assertEqual(
            metrics["quality"],
            {
                "windowDays": 30,
                "windowStartedAt": "2026-07-13T08:00:00Z",
                "completedWorkflowRuns": 2,
                "successfulWorkflowRuns": 1,
                "workflowSuccessRatePercent": 50.0,
            },
        )

    def test_output_is_deterministic_and_contains_no_source_identifiers_or_text(self):
        first = render_metrics(collect_metrics(fixture_client(), "mxx1111/clinical-ai-safety-kit", AS_OF))
        second = render_metrics(collect_metrics(fixture_client(), "mxx1111/clinical-ai-safety-kit", AS_OF))

        self.assertEqual(first, second)
        for forbidden in ("must-not-leak", "private-in-output", "alice", "bob", "carol"):
            self.assertNotIn(forbidden, first)
        parsed = json.loads(first)
        self.assertFalse(parsed["privacy"]["containsUserIdentifiers"])
        self.assertFalse(parsed["privacy"]["containsPatientData"])
        self.assertFalse(parsed["privacy"]["usesCookiesOrTracking"])
        self.assertNotIn("repository", parsed)
        self.assertNotIn("latestPublishedReleaseTag", parsed["activation"])
        self.assertNotIn("mxx1111", first)
        self.assertNotIn("v0.2.0", first)

        allowed_string_paths = {
            ("schemaVersion",),
            ("asOf",),
            ("privacy", "scope"),
            ("quality", "windowStartedAt"),
        }

        def assert_aggregate_leaf(value, path=()):
            if isinstance(value, dict):
                for key, child in value.items():
                    assert_aggregate_leaf(child, (*path, key))
            elif isinstance(value, str):
                self.assertIn(path, allowed_string_paths)
            else:
                self.assertTrue(value is None or isinstance(value, (bool, int, float)))

        assert_aggregate_leaf(parsed)

    def test_writes_exact_rendered_json(self):
        metrics = collect_metrics(fixture_client(), "mxx1111/clinical-ai-safety-kit", AS_OF)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "nested" / "metrics.json"
            write_metrics(output, metrics)
            self.assertEqual(output.read_text(encoding="utf-8"), render_metrics(metrics))

    def test_zero_denominators_are_reported_as_null(self):
        client = fixture_client()
        repo = "/repos/mxx1111/clinical-ai-safety-kit"
        client.responses.update(paged(f"{repo}/pulls", [], params={"state": "all"}))
        client.responses.update(
            paged(
                f"{repo}/actions/runs",
                [],
                params={"created": "2026-07-13T08:00:00Z..2026-08-12T08:00:00Z"},
                item_key="workflow_runs",
            )
        )

        metrics = collect_metrics(client, "mxx1111/clinical-ai-safety-kit", AS_OF)

        self.assertIsNone(metrics["contribution"]["pullRequestMergeRatePercent"])
        self.assertIsNone(metrics["quality"]["workflowSuccessRatePercent"])

    def test_rejects_invalid_repository_and_api_field_types(self):
        with self.assertRaisesRegex(MetricsError, "owner/name"):
            collect_metrics(fixture_client(), "https://github.com/owner/name", AS_OF)

        client = fixture_client()
        client.responses[("/repos/mxx1111/clinical-ai-safety-kit", ())]["stargazers_count"] = "7"
        with self.assertRaisesRegex(MetricsError, "stargazers_count"):
            collect_metrics(client, "mxx1111/clinical-ai-safety-kit", AS_OF)

    def test_paginates_plain_and_wrapped_api_collections(self):
        client = FakeClient({})
        client.responses.update(
            paged("/plain", [{"value": index} for index in range(100)] + [], params={"kind": "all"})
        )
        first_key = ("/plain", (("kind", "all"), ("page", "1"), ("per_page", "100")))
        client.responses[first_key] = [{"value": index} for index in range(100)]
        client.responses[("/plain", (("kind", "all"), ("page", "2"), ("per_page", "100")))] = [
            {"value": 100}
        ]
        client.responses[("/wrapped", (("page", "1"), ("per_page", "100")))] = {
            "total_count": 101,
            "workflow_runs": [{"value": index} for index in range(100)],
        }
        client.responses[("/wrapped", (("page", "2"), ("per_page", "100")))] = {
            "total_count": 101,
            "workflow_runs": [{"value": 100}],
        }

        self.assertEqual(len(_paginate(client, "/plain", params={"kind": "all"})), 101)
        self.assertEqual(
            len(_paginate(client, "/wrapped", item_key="workflow_runs", maximum_total_count=1_000)),
            101,
        )

    def test_fails_closed_when_actions_total_exceeds_exact_api_limit(self):
        client = FakeClient(
            {
                ("/actions", (("page", "1"), ("per_page", "100"))): {
                    "total_count": 1_001,
                    "workflow_runs": [{"value": index} for index in range(100)],
                }
            }
        )

        with self.assertRaisesRegex(MetricsError, "above the exact collection limit"):
            _paginate(client, "/actions", item_key="workflow_runs", maximum_total_count=1_000)

    def test_fails_closed_when_wrapped_page_count_is_incomplete(self):
        client = FakeClient(
            {
                ("/actions", (("page", "1"), ("per_page", "100"))): {
                    "total_count": 2,
                    "workflow_runs": [{"value": 0}],
                }
            }
        )

        with self.assertRaisesRegex(MetricsError, "incomplete paginated result"):
            _paginate(client, "/actions", item_key="workflow_runs", maximum_total_count=1_000)


class FakeHttpResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def read(self, limit):
        return self.payload[:limit]


class FakeOpener:
    def __init__(self, outcome):
        self.outcome = outcome

    def open(self, request, timeout):
        if isinstance(self.outcome, Exception):
            raise self.outcome
        return FakeHttpResponse(self.outcome)


class GitHubClientTest(unittest.TestCase):
    def test_requires_an_https_origin(self):
        for invalid in (
            "http://api.github.com",
            "https://api.github.com/path",
            "https://api.github.com?q=1",
            "https://user:password@api.github.com",
            "https://untrusted.example",
        ):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(MetricsError, "HTTPS origin"):
                GitHubClient(api_base_url=invalid)

    def test_rejects_redirects_without_exposing_destination_or_token(self):
        client = GitHubClient(token="secret-that-must-not-leak")
        response_body = io.BytesIO(b"redirect body")
        client._opener = FakeOpener(
            urllib.error.HTTPError(
                "https://api.github.com/repos/example/repo",
                302,
                "Found",
                {"Location": "https://untrusted.example/collect"},
                response_body,
            )
        )

        with self.assertRaises(MetricsError) as raised:
            client.get_json("/repos/example/repo")
        self.assertEqual(str(raised.exception), "GitHub API request failed with HTTP 302")
        self.assertNotIn("secret-that-must-not-leak", str(raised.exception))
        self.assertNotIn("untrusted.example", str(raised.exception))
        self.assertTrue(response_body.closed)

    def test_rejects_oversized_and_invalid_json_responses(self):
        client = GitHubClient()
        client._opener = FakeOpener(b"x" * (MAX_RESPONSE_BYTES + 1))
        with self.assertRaisesRegex(MetricsError, "exceeded"):
            client.get_json("/repos/example/repo")

        client._opener = FakeOpener(b"not-json")
        with self.assertRaisesRegex(MetricsError, "invalid JSON"):
            client.get_json("/repos/example/repo")

    def test_redacts_network_and_timeout_failures(self):
        client = GitHubClient(token="secret-that-must-not-leak")
        for failure in (urllib.error.URLError("sensitive-host"), TimeoutError("sensitive-timeout")):
            client._opener = FakeOpener(failure)
            with self.subTest(failure=failure), self.assertRaises(MetricsError) as raised:
                client.get_json("/repos/example/repo")
            self.assertEqual(str(raised.exception), "GitHub API request failed")

    def test_rejects_unbounded_timeout_values(self):
        for invalid in (0, -1, 61, float("inf"), float("nan")):
            with self.subTest(invalid=invalid), self.assertRaisesRegex(MetricsError, "timeout"):
                GitHubClient(timeout_seconds=invalid)


if __name__ == "__main__":
    unittest.main()
