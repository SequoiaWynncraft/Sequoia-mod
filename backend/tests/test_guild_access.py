"""Endpoint-level authorization tests; external membership and Mojang calls are stubbed."""
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient

# Importing the service initializes its database; never touch a developer's database.
with tempfile.TemporaryDirectory() as bootstrap_dir:
    with patch.dict(os.environ, {
        "RAID_PROFILES_DB": str(Path(bootstrap_dir) / "bootstrap.db"),
        "RAID_PROFILES_AUTH": "minecraft",
        "RAID_PROFILES_SECRET": "test-secret-not-for-deployment",
    }):
        import main

MEMBER_UUID = "10000000-0000-0000-0000-000000000001"
OUTSIDER_UUID = "10000000-0000-0000-0000-000000000002"
ENDPOINTS = (("GET", "/raid-profiles"), ("PUT", "/raid-profiles/me"), ("DELETE", "/raid-profiles/me"))


class GuildAccessTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        settings = patch.multiple(main, DATABASE_PATH=str(Path(directory.name) / "profiles.db"),
                                  AUTH_MODE="minecraft", AUTH_SECRET="test-secret-not-for-deployment")
        settings.start()
        self.addCleanup(settings.stop)
        main.create_tables()
        main.CHALLENGES.clear()
        self.roster = {"name": "Sequoia", "members": {
            "total": 1, "owner": {}, "recruit": {MEMBER_UUID: {"username": "RenamedMember"}}
        }}
        upstream_patch = patch.object(main.urllib.request, "urlopen", side_effect=self.roster_response)
        self.upstream = upstream_patch.start()
        self.addCleanup(upstream_patch.stop)
        self.client = TestClient(main.app)
        self.addCleanup(self.client.close)

    def roster_response(self, request, timeout):
        self.assertEqual(main.WYNNCRAFT_GUILD_URL, request.full_url)
        self.assertEqual(30, timeout)
        return io.BytesIO(json.dumps(self.roster).encode())

    def headers(self, uuid=MEMBER_UUID, username="OriginalName"):
        token, _ = main.issue_token(username, uuid)
        return {"Authorization": f"Bearer {token}"}

    def request(self, method, path, headers=None):
        kwargs = {"headers": headers or {}}
        if method == "PUT":
            kwargs["json"] = {"builds": ["ASCENDANCY"], "status": "ready"}
        return self.client.request(method, path, **kwargs)

    def assert_denied_on_all_endpoints(self, status, code, headers=None):
        for method, path in ENDPOINTS:
            with self.subTest(method=method):
                response = self.request(method, path, headers)
                self.assertEqual(status, response.status_code, response.text)
                self.assertEqual(code, response.json()["code"])
                self.assertEqual("no-store", response.headers["cache-control"])

    def test_unauthenticated_requests_cannot_read_or_write(self):
        self.assert_denied_on_all_endpoints(401, "token_invalid")
        self.upstream.assert_not_called()

    def test_supplied_member_identity_headers_do_not_authenticate(self):
        self.assert_denied_on_all_endpoints(401, "token_invalid", {
            "X-Minecraft-Username": "RenamedMember", "X-Minecraft-Uuid": MEMBER_UUID
        })
        self.upstream.assert_not_called()

    def test_invalid_token_cannot_read_or_write(self):
        self.assert_denied_on_all_endpoints(401, "token_invalid", {"Authorization": "Bearer forged.token"})
        self.upstream.assert_not_called()

    def test_nonmember_cannot_use_a_members_name_to_get_access(self):
        self.assert_denied_on_all_endpoints(403, "not_in_guild", self.headers(OUTSIDER_UUID, "RenamedMember"))
        with main.database() as connection:
            self.assertEqual(0, connection.execute("SELECT COUNT(*) FROM raid_profiles").fetchone()[0])

    def test_member_can_read_save_and_delete_after_renaming(self):
        headers = self.headers()
        self.assertEqual(200, self.request("PUT", "/raid-profiles/me", headers).status_code)
        response = self.request("GET", "/raid-profiles", headers)
        self.assertEqual(200, response.status_code)
        self.assertEqual(MEMBER_UUID, response.json()["profiles"][0]["minecraft"]["uuid"])
        self.assertEqual(204, self.request("DELETE", "/raid-profiles/me", headers).status_code)
        self.assertEqual([], self.request("GET", "/raid-profiles", headers).json()["profiles"])

    def test_existing_token_stops_working_after_departure(self):
        headers = self.headers()
        self.assertEqual(200, self.request("PUT", "/raid-profiles/me", headers).status_code)
        self.roster["members"] = {"total": 0, "recruit": {}}
        self.assert_denied_on_all_endpoints(403, "not_in_guild", headers)
        with main.database() as connection:
            self.assertEqual(1, connection.execute("SELECT COUNT(*) FROM raid_profiles").fetchone()[0])

    def test_outage_fails_closed_even_after_success(self):
        headers = self.headers()
        self.assertEqual(200, self.request("GET", "/raid-profiles", headers).status_code)
        self.upstream.side_effect = OSError("upstream down")
        self.assert_denied_on_all_endpoints(503, "guild_roster_unavailable", headers)

    def test_malformed_or_wrong_guild_roster_fails_closed(self):
        for roster in (None, [], {}, {"name": "Another Guild", "members": self.roster["members"]},
                       {"name": "Sequoia", "members": []},
                       {"name": "Sequoia", "members": {"recruit": "invalid"}},
                       {"name": "Sequoia", "members": {"recruit": {"invalid-uuid": {}}}}):
            with self.subTest(roster=roster):
                self.roster = roster
                self.assert_denied_on_all_endpoints(503, "guild_roster_unavailable", self.headers())

    def test_unreadable_roster_fails_closed(self):
        self.upstream.side_effect = lambda *args, **kwargs: io.BytesIO(b"not JSON")
        self.assert_denied_on_all_endpoints(503, "guild_roster_unavailable", self.headers())

    def test_uuid_normalization_preserves_membership(self):
        response = self.request("GET", "/raid-profiles", self.headers(MEMBER_UUID.replace("-", "")))
        self.assertEqual(200, response.status_code)

    def test_login_refuses_nonmember_even_after_mojang_verifies_identity(self):
        with patch.object(main, "ask_mojang", return_value={"id": OUTSIDER_UUID, "name": "Outsider"}):
            challenge = self.client.post("/auth/minecraft/challenge").json()
            response = self.client.post("/auth/minecraft/complete", json={
                "challenge_id": challenge["challenge_id"], "username": "Outsider"
            })
        self.assertEqual(403, response.status_code)
        self.assertEqual("not_in_guild", response.json()["code"])
        self.assertNotIn("token", response.json())

    def test_member_login_produces_usable_token(self):
        with patch.object(main, "ask_mojang", return_value={"id": MEMBER_UUID, "name": "RenamedMember"}):
            challenge = self.client.post("/auth/minecraft/challenge").json()
            response = self.client.post("/auth/minecraft/complete", json={
                "challenge_id": challenge["challenge_id"], "username": "RenamedMember"
            })
        self.assertEqual(200, response.status_code, response.text)
        headers = {"Authorization": "Bearer " + response.json()["token"]}
        self.assertEqual(200, self.request("GET", "/raid-profiles", headers).status_code)

    def test_disabled_mode_rejects_existing_tokens_and_new_signins(self):
        headers = self.headers()
        with patch.object(main, "AUTH_MODE", "disabled"):
            self.assert_denied_on_all_endpoints(503, "auth_not_configured", headers)
            self.assertEqual(503, self.client.post("/auth/minecraft/challenge").status_code)
            self.assertEqual(503, self.client.post("/auth/minecraft/complete", json={
                "challenge_id": "unused", "username": "Member"
            }).status_code)
        self.upstream.assert_not_called()

    def test_header_auth_configuration_is_rejected_at_startup(self):
        environment = dict(os.environ, RAID_PROFILES_AUTH="trust-header")
        result = subprocess.run([sys.executable, "-c", "import main"], cwd=Path(main.__file__).parent,
                                env=environment, capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("is not a mode this service knows", result.stderr)


if __name__ == "__main__":
    unittest.main()
