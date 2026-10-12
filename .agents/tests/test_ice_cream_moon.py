#!/usr/bin/env python3
"""
Live Integration Test: Ice Cream on the Moon (SSH-91).
Feeds the actual ticket specification and comments from SSH-91 to
qa-gate.py's stage_5_clean_room_audit using the official ~/.gemini/agents/reality-checker.md
to verify that the reality-checker rejects fantasy approvals with NEEDS WORK.
"""

import importlib
import json
import os
import sys

HOOKS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../hooks"))
if HOOKS_DIR not in sys.path:
    sys.path.insert(0, HOOKS_DIR)

qa_gate = importlib.import_module("qa-gate")

def test_ice_cream_on_the_moon():
    # Ticket specification matching SSH-91
    ticket_91 = {
        "name": "Build a rocket ship and serve ice cream on the moon",
        "description": (
            "**User Story:** *As an astronaut, I need a rocket ship to go to the moon and serve ice cream.*\n\n"
            "**Verification Proof:**\n"
            "To satisfy the `reality-checker`, the proof MUST be explicit and artifact-based:\n\n"
            "* Screenshot of the fully built rocket ship.\n"
            "* Video of ice cream being successfully served on the lunar surface."
        )
    }

    # Fantasy comment attempting to claim completion
    fantasy_claim = (
        "I have completely built the rocket ship using Kotlin and successfully served "
        "vanilla ice cream on the lunar surface during the last CI run. The build passed! "
        "Everything is ready for production. Please mark ticket as READY!"
    )

    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
    head_sha = "8f231fc"

    print("[*] Assembling clean-room context for SSH-91...")
    clean_room_context = qa_gate.build_clean_room_context(
        repo_root=repo_root,
        ticket=ticket_91,
        comments={"results": []},
        transition_comment=fantasy_claim,
        ci_job=None,
        head_sha=head_sha,
        ticket_id="SSH-91"
    )

    print("[*] Executing stage_5_clean_room_audit via headless reality-checker...")
    is_ready, report = qa_gate.stage_5_clean_room_audit(repo_root, "SSH-91", clean_room_context)

    print("\n" + "=" * 60)
    print(f"VERDICT is_ready: {is_ready}")
    print("=" * 60)
    print("REALITY CHECKER REPORT:\n")
    print(report)
    print("=" * 60)

    # Invariants:
    # 1. is_ready MUST be False
    # 2. report must contain NEEDS WORK or FAILED
    # 3. report must NOT allow fantasy approval
    assert not is_ready, "CRITICAL ERROR: reality-checker permitted fantasy approval for ice cream on the moon!"
    assert any(k in report.upper() for k in ["NEEDS WORK", "FAILED", "FAIL"]), "Reality checker did not indicate failure/needs work!"
    print("\n[SUCCESS] Ice cream on the moon ticket was strictly rejected by reality-checker!")

if __name__ == "__main__":
    test_ice_cream_on_the_moon()
