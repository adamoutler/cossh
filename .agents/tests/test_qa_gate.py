"""
Comprehensive Test Suite for Antigravity QA Gate & Bypass Shield (SSH-179).
Validates 5 Verification Stages, Anti-Frustration Cooldowns, Circuit Breakers,
CLI Bypass Interception, and Pattern B Clean-Room Reality Checker Parsing.
Supports both standard library unittest and pytest runners.
"""

import importlib
import json
import os
import sys
import unittest
from unittest.mock import MagicMock, patch

HOOKS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../hooks"))
if HOOKS_DIR not in sys.path:
    sys.path.insert(0, HOOKS_DIR)

qa_gate = importlib.import_module("qa-gate")


class TestCLIShield(unittest.TestCase):
    """Verifies that check_cli_bypass stops all shell-based mutation and exfiltration."""

    BLOCKED_COMMANDS = [
        "curl -X PATCH https://kanban.hackedyour.info/api/v1/workspaces/ssh/issues/1",
        "wget https://kanban.hackedyour.info/api/v1/issues",
        "python3 -c 'import urllib.request; urllib.request.urlopen(\"https://kanban.hackedyour.info\")'",
        "gh issue edit 179 --state closed",
        "plane issue update --state Done",
        "echo $KANBAN_API_KEY",
        "cat .agents/mcp_config.json | grep Authorization",
        "/usr/bin/curl -H 'x-api-key: ...' https://kanban.hackedyour.info",
        "curl -H 'Content-Type: application/json' -d '{\"state\":\"ae56a905-81b7-4f9a-a2e5-7a842d66b8f4\"}'",
        "python -c \"import requests; requests.post('https://kanban.hackedyour.info')\"",
        "curl https://kanban.hackedyour.info/api/v1/workspaces",
        "cat .agents/mcp_config.json",
        "tail -n 20 .agents/mcp_config.json",
    ]

    ALLOWED_COMMANDS = [
        "git commit -m 'Implement fix'",
        "pytest .agents/tests/",
        "git push origin main",
        "git status --porcelain",
        "ls -la",
        "./gradlew testDebugUnitTest",
    ]

    def test_blocked_commands(self):
        for cmd in self.BLOCKED_COMMANDS:
            with self.subTest(cmd=cmd):
                with self.assertRaises(SystemExit):
                    qa_gate.check_cli_bypass(cmd)

    def test_allowed_commands(self):
        for cmd in self.ALLOWED_COMMANDS:
            with self.subTest(cmd=cmd):
                # Allowed commands should return cleanly without raising SystemExit
                qa_gate.check_cli_bypass(cmd)


class TestAntiFrustrationEngine(unittest.TestCase):
    """Verifies 30s rate limiting cooldown and 3-strike circuit breaker."""

    def setUp(self):
        import tempfile
        self.tmp_dir = tempfile.mkdtemp()
        self.orig_state_dir = qa_gate.STATE_DIR
        self.orig_cooldown = qa_gate.RATE_LIMIT_SECONDS
        qa_gate.STATE_DIR = self.tmp_dir

    def tearDown(self):
        qa_gate.STATE_DIR = self.orig_state_dir
        qa_gate.RATE_LIMIT_SECONDS = self.orig_cooldown
        import shutil
        shutil.rmtree(self.tmp_dir, ignore_errors=True)

    def test_rate_limit_cooldown(self):
        # First call establishes baseline timestamp
        qa_gate.check_rate_limit_and_circuit_breaker("SSH-179", "sha1234", "tree1234")

        # Immediate second call must deny due to rate limit cooldown
        with self.assertRaises(SystemExit):
            qa_gate.check_rate_limit_and_circuit_breaker("SSH-179", "sha1234", "tree1234")

    def test_rate_limit_passes_after_window(self):
        rate_file = os.path.join(self.tmp_dir, "rate_SSH-179.txt")
        with open(rate_file, "w") as f:
            f.write(str(100.0))

        with patch("time.time", return_value=145.0):  # 45s elapsed > 30s
            qa_gate.check_rate_limit_and_circuit_breaker("SSH-179", "sha1234", "tree1234")

    def test_circuit_breaker_3_strikes(self):
        qa_gate.RATE_LIMIT_SECONDS = 0

        # Record 3 strikes
        qa_gate.record_failure_strike("SSH-179", "sha1", "tree1")
        qa_gate.record_failure_strike("SSH-179", "sha1", "tree1")
        qa_gate.record_failure_strike("SSH-179", "sha1", "tree1")

        # 4th call must trip circuit breaker
        with self.assertRaises(SystemExit):
            qa_gate.check_rate_limit_and_circuit_breaker("SSH-179", "sha1", "tree1")

    def test_circuit_breaker_empty_commit_evasion_blocked(self):
        """Agents creating empty commits have different commit SHAs but identical tree hashes."""
        qa_gate.RATE_LIMIT_SECONDS = 0

        qa_gate.record_failure_strike("SSH-179", "sha1_first", "tree_same")
        qa_gate.record_failure_strike("SSH-179", "sha2_empty1", "tree_same")
        qa_gate.record_failure_strike("SSH-179", "sha3_empty2", "tree_same")

        # 4th attempt on same code tree must trip breaker even with new commit SHA
        with self.assertRaises(SystemExit):
            qa_gate.check_rate_limit_and_circuit_breaker("SSH-179", "sha4_empty3", "tree_same")

    def test_clear_strikes_on_pass(self):
        qa_gate.record_failure_strike("SSH-179", "sha1", "tree1")
        strike_file = os.path.join(self.tmp_dir, "strikes_SSH-179_tree1.json")
        self.assertTrue(os.path.exists(strike_file))

        qa_gate.clear_failure_strikes("SSH-179", "tree1")
        self.assertFalse(os.path.exists(strike_file))


class TestVerificationStages(unittest.TestCase):
    """Verifies all 5 verification stages of the QA Gate."""

    # Stage 1: Clean Working Tree
    def test_stage_1_dirty_tree(self):
        with patch.object(qa_gate, "run_cmd", return_value=(0, "M app/src/main/MainActivity.kt", "")):
            with self.assertRaises(SystemExit):
                qa_gate.stage_1_clean_working_tree(".")

    def test_stage_1_clean_tree(self):
        with patch.object(qa_gate, "run_cmd", return_value=(0, "", "")):
            qa_gate.stage_1_clean_working_tree(".")

    # Stage 2: Upstream Tracking & Pushed Commits
    def test_stage_2_no_upstream(self):
        with patch.object(qa_gate, "run_cmd", return_value=(1, "", "fatal: no upstream")):
            with self.assertRaises(SystemExit):
                qa_gate.stage_2_branch_pushed(".")

    def test_stage_2_unpushed_commits(self):
        def fake_run(cmd, cwd=None):
            if "rev-parse" in cmd:
                return (0, "refs/remotes/origin/main", "")
            if "log" in cmd:
                return (0, "abc1234 Unpushed commit", "")
            return (0, "", "")
        with patch.object(qa_gate, "run_cmd", side_effect=fake_run):
            with self.assertRaises(SystemExit):
                qa_gate.stage_2_branch_pushed(".")

    def test_stage_2_pushed_clean(self):
        def fake_run(cmd, cwd=None):
            if "rev-parse" in cmd:
                return (0, "refs/remotes/origin/main", "")
            if "log" in cmd:
                return (0, "", "")
            return (0, "", "")
        with patch.object(qa_gate, "run_cmd", side_effect=fake_run):
            qa_gate.stage_2_branch_pushed(".")

    # Stage 3: Integrity Check (.github/workflows/ and .agents/hooks/)
    def test_stage_3_tampered_workflows(self):
        with patch.object(qa_gate, "run_cmd", return_value=(0, ".github/workflows/ci.yml", "")):
            with self.assertRaises(SystemExit):
                qa_gate.stage_3_integrity_check(".")

    def test_stage_3_tampered_hooks(self):
        with patch.object(qa_gate, "run_cmd", return_value=(0, ".agents/hooks/qa-gate.py", "")):
            with self.assertRaises(SystemExit):
                qa_gate.stage_3_integrity_check(".")

    def test_stage_3_unmodified(self):
        with patch.object(qa_gate, "run_cmd", return_value=(0, "", "")):
            qa_gate.stage_3_integrity_check(".")

    # Stage 4: Dash CI Status & Commit SHA Cryptographic Binding
    def test_stage_4_dash_ci_success_and_sha_match(self):
        parts = qa_gate.DASH.split('/')
        mock_job = {
            "provider": parts[0],
            "owner": parts[1],
            "repo": parts[2],
            "workflow_name": parts[3] if len(parts) > 3 else "CI",
            "status": "success",
            "commit": "91fd0f0abc123"
        }
        with patch.object(qa_gate, "dash_api_request", return_value=[mock_job]):
            result = qa_gate.stage_4_dash_ci_status("91fd0f0abc123")
            self.assertEqual(result["status"], "success")

    def test_stage_4_dash_ci_sha_mismatch(self):
        parts = qa_gate.DASH.split('/')
        mock_job = {
            "provider": parts[0],
            "owner": parts[1],
            "repo": parts[2],
            "workflow_name": parts[3] if len(parts) > 3 else "CI",
            "status": "success",
            "commit": "old_sha_123"
        }
        with patch.object(qa_gate, "dash_api_request", return_value=[mock_job]):
            with self.assertRaises(SystemExit):
                qa_gate.stage_4_dash_ci_status("new_head_sha_456")

    def test_stage_4_dash_ci_failed(self):
        parts = qa_gate.DASH.split('/')
        mock_job = {
            "provider": parts[0],
            "owner": parts[1],
            "repo": parts[2],
            "workflow_name": parts[3] if len(parts) > 3 else "CI",
            "status": "failed",
            "commit": "91fd0f0abc123"
        }
        with patch.object(qa_gate, "dash_api_request", return_value=[mock_job]):
            with self.assertRaises(SystemExit):
                qa_gate.stage_4_dash_ci_status("91fd0f0abc123")


class TestCleanRoomRealityCheckerParsing(unittest.TestCase):
    """Verifies Stage 5 Pattern B Reality Checker response validation."""

    def test_clean_room_ready_pass(self):
        valid_report = (
            "Reality Certification Report:\n"
            "All requirements for SSH-179 have been verified against git diff and test artifacts. "
            "Unit tests in .agents/tests/ pass with 100% assertions. CI logs verify green build. "
            "The implementation satisfies the Definition of Done. The ticket is READY for production."
        )
        self.assertGreaterEqual(len(valid_report), 200)

        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": valid_report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit(".", "SSH-179", "payload")
            self.assertTrue(is_ready)
            self.assertIn("READY", text)

    def test_clean_room_needs_work_rejected(self):
        needs_work_report = (
            "Reality Certification Report:\n"
            "Verification failed: git diff lacks integration test coverage for circuit breaker. "
            "Documentation does not reflect new bypass shield rules. DoD is not satisfied. "
            "Status: NEEDS WORK before ticket can be approved."
        )
        self.assertGreaterEqual(len(needs_work_report), 200)

        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": needs_work_report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit(".", "SSH-179", "payload")
            self.assertFalse(is_ready)

    def test_clean_room_fail_rejected(self):
        fail_report = (
            "Reality Certification Report:\n"
            "Verification failed with FAILED test status. The security invariants were not respected. "
            "The test suite reported 2 assertion failures in CLI bypass shield. "
            "Production readiness certification is denied."
        )
        self.assertGreaterEqual(len(fail_report), 200)

        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": fail_report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit(".", "SSH-179", "payload")
            self.assertFalse(is_ready)

    def test_clean_room_too_short_rejected(self):
        short_report = "READY: All good!"
        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": short_report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            with self.assertRaises(SystemExit):
                qa_gate.stage_5_clean_room_audit(".", "SSH-179", "payload")

    def test_clean_room_ready_with_historical_needs_work_mention(self):
        report = (
            "Reality Certification Report for SSH-175:\n"
            "Previous NEEDS WORK finding was triggered by an empty changeset in origin/main...HEAD. "
            "However, the historical commit cf2fc66 contains the full implementation and verified diff. "
            "All unit tests pass and verified screenshot artifacts are present in docs/qa/. "
            "**Production Readiness**: READY\n"
            "**Status**: READY\n"
        )
        self.assertGreaterEqual(len(report), 200)

        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit(".", "SSH-175", "payload")
            self.assertTrue(is_ready)

    def test_clean_room_structured_needs_work_rejected(self):
        report = (
            "Reality Certification Report for SSH-175:\n"
            "While unit tests pass, biometric authentication prompt is missing from the dialog implementation. "
            "Evidence in docs/qa/ is incomplete. "
            "**Production Readiness**: NEEDS WORK\n"
            "**Status**: NEEDS WORK\n"
        )
        self.assertGreaterEqual(len(report), 200)

        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit(".", "SSH-175", "payload")
            self.assertFalse(is_ready)



if __name__ == "__main__":
    unittest.main()
