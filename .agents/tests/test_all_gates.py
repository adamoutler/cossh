#!/usr/bin/env python3
"""
Verification Suite for All Antigravity QA Gates:
1. Commit Gate: Pristine working tree (Stage 1).
2. Push Gate: Pushed to upstream branch (Stage 2).
3. Test Gate (CI): Green Dash CI build matching HEAD SHA (Stage 4).
4. Reality Gate: Clean-room reality-checker audit logging alongside agent comments (Stage 5).
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


class TestAllGates(unittest.TestCase):
    """Rigorous verification of Commit, Test, Push, and Reality gates."""

    # =========================================================================
    # GATE 1: COMMIT GATE (Working Tree Cleanliness)
    # =========================================================================
    def test_gate_1_commit_dirty_fails(self):
        """Commit Gate must reject transitions when uncommitted changes exist."""
        with patch.object(qa_gate, "run_cmd", return_value=(0, "M app/src/main/File.kt", "")):
            with self.assertRaises(SystemExit) as cm:
                qa_gate.stage_1_clean_working_tree("/fake/repo")

    def test_gate_1_commit_clean_passes(self):
        """Commit Gate must pass when git working tree is clean."""
        with patch.object(qa_gate, "run_cmd", return_value=(0, "", "")):
            # Should complete without error
            qa_gate.stage_1_clean_working_tree("/fake/repo")

    # =========================================================================
    # GATE 2: PUSH GATE (Remote Branch Synchronization)
    # =========================================================================
    def test_gate_2_push_unpushed_commits_fails(self):
        """Push Gate must reject transitions when local commits are not pushed."""
        def fake_run(cmd, cwd=None):
            if "rev-parse" in cmd:
                return (0, "refs/remotes/origin/main", "")
            if "log" in cmd:
                return (0, "abc1234 Unpushed local commit", "")
            return (0, "", "")

        with patch.object(qa_gate, "run_cmd", side_effect=fake_run):
            with self.assertRaises(SystemExit):
                qa_gate.stage_2_branch_pushed("/fake/repo")

    def test_gate_2_push_no_upstream_fails(self):
        """Push Gate must reject transitions when branch has no upstream."""
        def fake_run(cmd, cwd=None):
            if "rev-parse" in cmd:
                return (1, "", "fatal: no upstream configured")
            return (0, "", "")

        with patch.object(qa_gate, "run_cmd", side_effect=fake_run):
            with self.assertRaises(SystemExit):
                qa_gate.stage_2_branch_pushed("/fake/repo")

    def test_gate_2_push_synced_passes(self):
        """Push Gate must pass when all commits are pushed to upstream."""
        def fake_run(cmd, cwd=None):
            if "rev-parse" in cmd:
                return (0, "refs/remotes/origin/main", "")
            if "log" in cmd:
                return (0, "", "")
            return (0, "", "")

        with patch.object(qa_gate, "run_cmd", side_effect=fake_run):
            qa_gate.stage_2_branch_pushed("/fake/repo")

    # =========================================================================
    # GATE 3: TEST / CI GATE (Dash Build Status & SHA Binding)
    # =========================================================================
    def test_gate_3_test_ci_failure_fails(self):
        """Test Gate must reject transitions when CI pipeline fails."""
        mock_job = {
            "provider": "github",
            "owner": "adamoutler",
            "repo": "cossh",
            "workflow_name": "CI",
            "status": "failed",
            "commit": "abc1234567"
        }
        with patch.object(qa_gate, "dash_api_request", return_value=[mock_job]):
            with self.assertRaises(SystemExit):
                qa_gate.stage_4_dash_ci_status("abc1234567")

    def test_gate_3_test_ci_sha_mismatch_fails(self):
        """Test Gate must reject transitions when Dash CI is for an older commit."""
        mock_job = {
            "provider": "github",
            "owner": "adamoutler",
            "repo": "cossh",
            "workflow_name": "CI",
            "status": "success",
            "commit": "old_commit_111"
        }
        with patch.object(qa_gate, "dash_api_request", return_value=[mock_job]):
            with self.assertRaises(SystemExit):
                qa_gate.stage_4_dash_ci_status("new_head_222")

    def test_gate_3_test_ci_success_passes(self):
        """Test Gate must pass when Dash CI is success and SHA matches HEAD."""
        parts = qa_gate.DASH.split('/')
        mock_job = {
            "provider": parts[0],
            "owner": parts[1],
            "repo": parts[2],
            "workflow_name": parts[3] if len(parts) > 3 else "CI",
            "status": "success",
            "commit": "feedc0de123"
        }
        with patch.object(qa_gate, "dash_api_request", return_value=[mock_job]):
            job = qa_gate.stage_4_dash_ci_status("feedc0de123")
            self.assertEqual(job["status"], "success")

    # =========================================================================
    # GATE 4: REALITY GATE & COMMENT LOGGING
    # =========================================================================
    def test_gate_4_reality_needs_work_rejected(self):
        """Reality Gate must reject tickets when reality checker issues NEEDS WORK."""
        report = (
            "Reality Certification Report:\n"
            "Checked implementation against requirements. Missing automated UI screenshots.\n"
            "Unit tests fail on Robolectric when running the crypto keystore cipher suite.\n"
            "Artifacts in docs/qa/ are incomplete. Definition of Done not met. Status: NEEDS WORK."
        )
        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit("/fake/repo", "SSH-99", "context")
            self.assertFalse(is_ready)
            self.assertIn("NEEDS WORK", text)

    def test_gate_4_reality_ready_accepted(self):
        """Reality Gate must pass when reality checker certifies READY with full evidence."""
        report = (
            "Reality Certification Report:\n"
            "Thoroughly verified git diff and 3 unit tests. Paparazzi UI screenshots validated.\n"
            "Build logs show 100% test pass. Definition of Done satisfied. Status: READY for deployment."
        )
        mock_proc = MagicMock()
        mock_proc.communicate.return_value = (json.dumps({"response": report}), "")
        mock_proc.returncode = 0

        with patch("subprocess.Popen", return_value=mock_proc):
            is_ready, text = qa_gate.stage_5_clean_room_audit("/fake/repo", "SSH-99", "context")
            self.assertTrue(is_ready)
            self.assertIn("READY", text)

    def test_gate_4_reality_comment_logged_alongside_agent_comment(self):
        """Verifies that the reality checker report is logged alongside the agent comment."""
        agent_comment = "Completed SSH-99: implemented secure keystore fallback and added 4 unit tests."
        reality_report = (
            "Reality Certification Report:\n"
            "Checked Keystore fallback in SecurePreferences.kt. All 4 unit tests passing.\n"
            "Artifacts verified in docs/qa/SSH-99.md. Verdict: READY."
        )

        html_out = qa_gate.format_qa_comment(
            ticket_id="SSH-99",
            head_sha="a1b2c3d4e5",
            tree_hash="f6e5d4c3b2",
            transition_comment=agent_comment,
            result_text=reality_report
        )

        # 1. Both agent submission and reality checker sections must be present
        self.assertIn("Agent Submission Comment", html_out)
        self.assertIn("Reality Checker Report", html_out)

        # 2. Agent's exact comment must be contained
        self.assertIn("implemented secure keystore fallback", html_out)

        # 3. Reality checker's exact report must be contained
        self.assertIn("All 4 unit tests passing", html_out)

        # 4. Must contain cryptographic commit reference
        self.assertIn("a1b2c3d", html_out)


if __name__ == "__main__":
    unittest.main()
