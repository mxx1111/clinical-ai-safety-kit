#!/usr/bin/env python3
"""Collect privacy-safe, repository-level GitHub metrics as deterministic JSON."""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


API_VERSION = "2022-11-28"
DEFAULT_API_BASE_URL = "https://api.github.com"
MAX_RESPONSE_BYTES = 5_000_000
PAGE_SIZE = 100
MAX_PAGES = 100
REPOSITORY_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class MetricsError(RuntimeError):
    """A safe-to-display collection or validation failure."""


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        return None


class GitHubClient:
    """Small read-only GitHub REST client with bounded responses and no redirects."""

    def __init__(
        self,
        token: str | None = None,
        *,
        api_base_url: str = DEFAULT_API_BASE_URL,
        timeout_seconds: float = 20.0,
    ) -> None:
        parsed = urllib.parse.urlparse(api_base_url)
        if (
            parsed.scheme != "https"
            or parsed.hostname != "api.github.com"
            or parsed.port is not None
            or parsed.username is not None
            or parsed.password is not None
            or parsed.path not in {"", "/"}
            or parsed.params
            or parsed.query
            or parsed.fragment
        ):
            raise MetricsError("GitHub API base URL must be an HTTPS origin")
        self._token = token
        if not math.isfinite(timeout_seconds) or timeout_seconds <= 0 or timeout_seconds > 60:
            raise MetricsError("GitHub API timeout must be greater than 0 and no more than 60 seconds")
        self._api_base_url = api_base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds
        self._opener = urllib.request.build_opener(_NoRedirectHandler())

    def get_json(self, path: str, params: dict[str, Any] | None = None) -> Any:
        if not path.startswith("/") or path.startswith("//"):
            raise MetricsError("GitHub API path must be absolute and host-relative")
        query = urllib.parse.urlencode(params or {})
        url = f"{self._api_base_url}{path}"
        if query:
            url = f"{url}?{query}"

        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "clinical-ai-safety-kit-public-metrics",
            "X-GitHub-Api-Version": API_VERSION,
        }
        if self._token:
            headers["Authorization"] = f"Bearer {self._token}"

        request = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with self._opener.open(request, timeout=self._timeout_seconds) as response:
                raw = response.read(MAX_RESPONSE_BYTES + 1)
        except urllib.error.HTTPError as exc:
            status = exc.code
            if exc.fp is not None:
                exc.close()
            raise MetricsError(f"GitHub API request failed with HTTP {status}") from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise MetricsError("GitHub API request failed") from None

        if len(raw) > MAX_RESPONSE_BYTES:
            raise MetricsError("GitHub API response exceeded the configured limit")
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            raise MetricsError("GitHub API returned invalid JSON") from None


def _parse_repository(repository: str) -> tuple[str, str]:
    if not REPOSITORY_PATTERN.fullmatch(repository):
        raise MetricsError("repository must use the owner/name format")
    owner, name = repository.split("/", 1)
    if owner in {".", ".."} or name in {".", ".."}:
        raise MetricsError("repository must use the owner/name format")
    return owner, name


def _parse_timestamp(value: str, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (TypeError, ValueError):
        raise MetricsError(f"{field} must be an ISO-8601 timestamp") from None
    if parsed.tzinfo is None:
        raise MetricsError(f"{field} must include a timezone")
    return parsed.astimezone(timezone.utc)


def _format_timestamp(value: datetime) -> str:
    return value.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _require_dict(value: Any, field: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise MetricsError(f"GitHub API field {field} must be an object")
    return value


def _require_list(value: Any, field: str) -> list[Any]:
    if not isinstance(value, list):
        raise MetricsError(f"GitHub API field {field} must be an array")
    return value


def _require_int(value: Any, field: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise MetricsError(f"GitHub API field {field} must be a non-negative integer")
    return value


def _paginate(
    client: Any,
    path: str,
    *,
    params: dict[str, Any] | None = None,
    item_key: str | None = None,
    maximum_total_count: int | None = None,
) -> list[dict[str, Any]]:
    collected: list[dict[str, Any]] = []
    base_params = dict(params or {})
    declared_total: int | None = None
    for page in range(1, MAX_PAGES + 1):
        response = client.get_json(path, {**base_params, "per_page": PAGE_SIZE, "page": page})
        if item_key is not None:
            wrapper = _require_dict(response, path)
            if maximum_total_count is not None:
                page_total = _require_int(wrapper.get("total_count"), f"{path}.total_count")
                if declared_total is None:
                    declared_total = page_total
                    if declared_total > maximum_total_count:
                        raise MetricsError(
                            f"{path} reported {declared_total} items, above the exact collection limit of "
                            f"{maximum_total_count}"
                        )
                elif page_total != declared_total:
                    raise MetricsError(f"{path} total_count changed during pagination")
            response = wrapper.get(item_key)
        items = _require_list(response, item_key or path)
        for item in items:
            collected.append(_require_dict(item, item_key or path))
        if len(items) < PAGE_SIZE:
            if declared_total is not None and len(collected) != declared_total:
                raise MetricsError(f"{path} returned an incomplete paginated result")
            return collected
    raise MetricsError(f"GitHub API pagination exceeded {MAX_PAGES} pages")


def _count_issues(issues: list[dict[str, Any]], as_of: datetime) -> dict[str, int]:
    counts = {"open": 0, "closed": 0}
    for issue in issues:
        if "pull_request" in issue:
            continue
        created_at = _parse_timestamp(issue.get("created_at"), "issue.created_at")
        if created_at > as_of:
            continue
        state = issue.get("state")
        if state not in counts:
            raise MetricsError("GitHub API field issue.state must be open or closed")
        counts[state] += 1
    return counts


def _rate(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return round((numerator / denominator) * 100, 2)


def collect_metrics(client: Any, repository: str, as_of: datetime) -> dict[str, Any]:
    """Collect aggregate counts only; no account, title, body, or identifier is returned."""

    owner, name = _parse_repository(repository)
    repo_path = f"/repos/{owner}/{name}"
    as_of = as_of.astimezone(timezone.utc)
    window_start = as_of - timedelta(days=30)

    repo = _require_dict(client.get_json(repo_path), "repository")
    releases = _paginate(client, f"{repo_path}/releases")
    feedback_issues = _paginate(client, f"{repo_path}/issues", params={"state": "all", "labels": "feedback"})
    agent_tasks = _paginate(client, f"{repo_path}/issues", params={"state": "all", "labels": "agent-task"})
    pulls = _paginate(client, f"{repo_path}/pulls", params={"state": "all"})
    workflow_runs = _paginate(
        client,
        f"{repo_path}/actions/runs",
        params={"created": f"{_format_timestamp(window_start)}..{_format_timestamp(as_of)}"},
        item_key="workflow_runs",
        maximum_total_count=1_000,
    )

    published_releases: list[dict[str, Any]] = []
    for release in releases:
        if release.get("draft") is True:
            continue
        published_at_raw = release.get("published_at")
        if published_at_raw is None:
            continue
        published_at = _parse_timestamp(published_at_raw, "release.published_at")
        if published_at <= as_of:
            published_releases.append(release)
    published_releases.sort(
        key=lambda release: _parse_timestamp(release["published_at"], "release.published_at"),
        reverse=True,
    )
    release_downloads = 0
    for release in published_releases:
        for asset in _require_list(release.get("assets", []), "release.assets"):
            asset_object = _require_dict(asset, "release.asset")
            release_downloads += _require_int(asset_object.get("download_count"), "asset.download_count")

    feedback = _count_issues(feedback_issues, as_of)
    tasks = _count_issues(agent_tasks, as_of)

    opened_pulls = 0
    merged_pulls = 0
    for pull in pulls:
        created_at = _parse_timestamp(pull.get("created_at"), "pull.created_at")
        if created_at > as_of:
            continue
        opened_pulls += 1
        merged_at_raw = pull.get("merged_at")
        if merged_at_raw is not None and _parse_timestamp(merged_at_raw, "pull.merged_at") <= as_of:
            merged_pulls += 1

    completed_runs = 0
    successful_runs = 0
    for run in workflow_runs:
        created_at = _parse_timestamp(run.get("created_at"), "workflow_run.created_at")
        if created_at < window_start or created_at > as_of:
            continue
        if run.get("status") != "completed":
            continue
        completed_runs += 1
        if run.get("conclusion") == "success":
            successful_runs += 1

    return {
        "schemaVersion": "1.0",
        "asOf": _format_timestamp(as_of),
        "privacy": {
            "scope": "public repository-level aggregate counts only",
            "containsUserIdentifiers": False,
            "containsPatientData": False,
            "usesCookiesOrTracking": False,
        },
        "reach": {
            "stars": _require_int(repo.get("stargazers_count"), "repository.stargazers_count"),
            "forks": _require_int(repo.get("forks_count"), "repository.forks_count"),
            "subscribers": _require_int(repo.get("subscribers_count"), "repository.subscribers_count"),
        },
        "activation": {
            "publishedReleases": len(published_releases),
            "releaseAssetDownloads": release_downloads,
            "firstUserFeedbackSubmissions": feedback["open"] + feedback["closed"],
        },
        "feedback": {
            "open": feedback["open"],
            "closed": feedback["closed"],
            "total": feedback["open"] + feedback["closed"],
        },
        "contribution": {
            "pullRequestsOpened": opened_pulls,
            "pullRequestsMerged": merged_pulls,
            "pullRequestMergeRatePercent": _rate(merged_pulls, opened_pulls),
            "agentTasksOpen": tasks["open"],
            "agentTasksClosed": tasks["closed"],
        },
        "quality": {
            "windowDays": 30,
            "windowStartedAt": _format_timestamp(window_start),
            "completedWorkflowRuns": completed_runs,
            "successfulWorkflowRuns": successful_runs,
            "workflowSuccessRatePercent": _rate(successful_runs, completed_runs),
        },
    }


def render_metrics(metrics: dict[str, Any]) -> str:
    return json.dumps(metrics, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def write_metrics(path: Path, metrics: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(render_metrics(metrics), encoding="utf-8")


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"), help="GitHub owner/name")
    parser.add_argument("--output", type=Path, default=Path("target/public-metrics.json"))
    parser.add_argument("--as-of", help="UTC ISO-8601 collection timestamp; defaults to the current time")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    if not args.repository:
        print("error: --repository or GITHUB_REPOSITORY is required", file=sys.stderr)
        return 2
    try:
        as_of = _parse_timestamp(args.as_of, "as-of") if args.as_of else datetime.now(timezone.utc)
        client = GitHubClient(os.environ.get("GITHUB_TOKEN"))
        metrics = collect_metrics(client, args.repository, as_of)
        write_metrics(args.output, metrics)
    except MetricsError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(f"Wrote privacy-safe public metrics to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
