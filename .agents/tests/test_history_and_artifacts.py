#!/usr/bin/env python3
import os
import sys
import unittest
from unittest.mock import patch, MagicMock

# Add hooks directory to sys.path
HOOKS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../hooks"))
sys.path.insert(0, HOOKS_DIR)

import importlib.util
spec = importlib.util.spec_from_file_location("qa_gate", os.path.join(HOOKS_DIR, "qa-gate.py"))
qa_gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qa_gate)

class TestHistoryAndArtifacts(unittest.TestCase):

    def test_verify_binary_artifact_valid_png(self):
        test_png = "/tmp/test_valid.png"
        with open(test_png, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n" + b"\x00" * 1024)
        try:
            with patch.object(qa_gate, "run_cmd", return_value=(0, "test_valid.png\n", "")):
                res = qa_gate.verify_binary_artifact("/tmp", "test_valid.png")
                self.assertIn("PNG image", res)
                self.assertIn("tracked in git", res)
        finally:
            if os.path.exists(test_png):
                os.remove(test_png)

    def test_verify_binary_artifact_too_small(self):
        test_png = "/tmp/test_small.png"
        with open(test_png, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n")
        try:
            res = qa_gate.verify_binary_artifact("/tmp", "test_small.png")
            self.assertIn("too small", res)
        finally:
            if os.path.exists(test_png):
                os.remove(test_png)

    def test_verify_binary_artifact_fake_magic(self):
        test_png = "/tmp/test_fake.png"
        with open(test_png, "wb") as f:
            f.write(b"NOT_A_PNG_FILE!!" + b"\x00" * 2000)
        try:
            res = qa_gate.verify_binary_artifact("/tmp", "test_fake.png")
            self.assertIn("invalid image header", res)
        finally:
            if os.path.exists(test_png):
                os.remove(test_png)

    def test_resolve_ticket_commits_word_boundary(self):
        with patch.object(qa_gate, "run_cmd") as mock_cmd:
            # git log returns commits
            mock_cmd.side_effect = [
                (0, "hash1\nhash2\n", ""), # git log --grep
                (0, "hash1\nhash2\nhash3\n", ""), # git log HEAD -n50
                (0, "1\n", ""), # rev-list count
                (0, "parent_hash\n", ""), # rev-parse parent
                (0, "file1.kt\nfile2.kt\n", ""), # git show --name-only
            ]
            commits, is_contiguous, parent, files = qa_gate.resolve_ticket_commits("/tmp", "SSH-175", "hash1")
            self.assertEqual(len(commits), 2)
            self.assertTrue(is_contiguous)
            self.assertEqual(parent, "parent_hash")
            self.assertEqual(files, ["file1.kt", "file2.kt"])

if __name__ == "__main__":
    unittest.main()
