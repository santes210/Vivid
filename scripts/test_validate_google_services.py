#!/usr/bin/env python3
"""Tests de scripts/validate-google-services.py (sin Firebase)."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SCRIPT = ROOT / "validate-google-services.py"


def run(path: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(SCRIPT), str(path)],
        capture_output=True,
        text=True,
    )


def write(payload: dict) -> Path:
    tmp = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
    json.dump(payload, tmp)
    tmp.close()
    return Path(tmp.name)


def test_ok():
    path = write({
        "client": [{
            "client_info": {"android_client_info": {"package_name": "com.vivid.app"}},
            "oauth_client": [
                {"client_type": 3, "client_id": "123.apps.googleusercontent.com"},
                {
                    "client_type": 1,
                    "android_client_info": {"certificate_hash": "aabbccddeeff0011"},
                },
            ],
        }]
    })
    result = run(path)
    path.unlink()
    assert result.returncode == 0, result.stdout + result.stderr
    assert "OK:" in result.stdout


def test_missing_web_client():
    path = write({
        "client": [{
            "client_info": {"android_client_info": {"package_name": "com.vivid.app"}},
            "oauth_client": [
                {
                    "client_type": 1,
                    "android_client_info": {"certificate_hash": "aabbccddeeff0011"},
                },
            ],
        }]
    })
    result = run(path)
    path.unlink()
    assert result.returncode == 1
    assert "client_type 3" in result.stdout


def test_missing_sha1():
    path = write({
        "client": [{
            "client_info": {"android_client_info": {"package_name": "com.vivid.app"}},
            "oauth_client": [
                {"client_type": 3, "client_id": "123.apps.googleusercontent.com"},
            ],
        }]
    })
    result = run(path)
    path.unlink()
    assert result.returncode == 0
    assert "SHA-1" in result.stdout


def test_wrong_package():
    path = write({
        "client": [{
            "client_info": {"android_client_info": {"package_name": "com.other.app"}},
            "oauth_client": [{"client_type": 3}],
        }]
    })
    result = run(path)
    path.unlink()
    assert result.returncode == 1
    assert "com.vivid.app" in result.stdout


if __name__ == "__main__":
    test_ok()
    test_missing_web_client()
    test_missing_sha1()
    test_wrong_package()
    print("scripts/validate-google-services.py: 4 tests ok")
