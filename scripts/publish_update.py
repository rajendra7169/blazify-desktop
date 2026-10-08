#!/usr/bin/env python3
"""Puts a release's installers on the mirror and writes the update file the app reads.

Blazify asks rajendrapandey.info.np/blazify/desktop.json whether there is a newer
version, and fetches the installer from the first address listed for it that
answers: the GitLab package registry, then the GitHub release. This script
uploads the files in release/ to GitLab and writes that update file into the
website's folder, committed but not pushed. Run it once the release is ready:

    scripts/publish_update.py 1.2.1

The files are taken from release/ by their published names (Blazify.deb,
Blazify.AppImage, Blazify-linux.tar.gz, Blazify-setup.exe); a missing one is
left out. The notes are release/notes.md. The GitLab token is read from
~/.config/blazify-gitlab-token and never printed.
"""
import datetime
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
RELEASE = REPO / "release"
FILES = ["Blazify.deb", "Blazify.AppImage", "Blazify-linux.tar.gz", "Blazify-setup.exe"]
GITLAB_PROJECT = os.environ.get("GITLAB_PROJECT", "hackerraja7169/blazify")
GITLAB = "https://gitlab.com/api/v4/projects/" + urllib.parse.quote(GITLAB_PROJECT, safe="")
PACKAGE = "blazify-desktop"
GITHUB_DOWNLOADS = "https://github.com/rajendra7169/blazify-desktop/releases/download"
GITHUB_PAGE = "https://github.com/rajendra7169/blazify-desktop/releases/tag"
SITE = Path("~/Documents/GitHub/portfolio-site").expanduser()


def gitlab_token():
    return Path("~/.config/blazify-gitlab-token").expanduser().read_text().strip()


def to_gitlab(version, path):
    """A plain file at a fixed address in the package registry; uploaded only when not already there."""
    url = f"{GITLAB}/packages/generic/{PACKAGE}/{version}/{path.name}"
    size = path.stat().st_size
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method="HEAD"), timeout=60) as r:
            if int(r.headers.get("Content-Length", -1)) == size:
                print(f"  GitLab {path.name}: already there")
                return url
    except urllib.error.HTTPError:
        pass
    with path.open("rb") as body:
        request = urllib.request.Request(
            url, data=body, method="PUT",
            headers={"PRIVATE-TOKEN": gitlab_token(), "Content-Type": "application/octet-stream",
                     "Content-Length": str(size)},
        )
        with urllib.request.urlopen(request, timeout=1800):
            pass
    print(f"  GitLab {path.name}: uploaded ({size // 1_000_000} MB)")
    return url


def main(version):
    version = version.removeprefix("v")
    listed = []
    for name in FILES:
        path = RELEASE / name
        if not path.exists():
            print(f"  {name}: not in release/, left out")
            continue
        listed.append({
            "name": name,
            "size": path.stat().st_size,
            "urls": [to_gitlab(version, path), f"{GITHUB_DOWNLOADS}/v{version}/{name}"],
        })
    if not listed:
        sys.exit("Nothing in release/ to publish")

    notes = RELEASE / "notes.md"
    update = {
        "_about": "Blazify for desktop reads this to learn of a new version, and fetches "
                  "each file from the first of its urls that answers.",
        "version": version,
        "date": datetime.date.today().isoformat(),
        "page": f"{GITHUB_PAGE}/v{version}",
        "notes": notes.read_text() if notes.exists() else "",
        "files": listed,
    }
    out = SITE / "public/blazify/desktop.json"
    out.write_text(json.dumps(update, indent=2, ensure_ascii=False) + "\n")
    git = ["git", "-C", str(SITE), "-c", "user.name=Rajendra Pandey",
           "-c", "user.email=rajendrapandey199971@gmail.com"]
    subprocess.run(git + ["add", "public/blazify/desktop.json"], check=True)
    if subprocess.run(git + ["diff", "--cached", "--quiet"]).returncode:
        subprocess.run(git + ["commit", "-q", "-m", f"Blazify for desktop {version} update file"], check=True)
        print("Update file committed. Publish it with:\n"
              f"  git -C {SITE} push https://github.com/rajendra7169/portfolio.git site-main:main")
    else:
        print("Update file already up to date")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: scripts/publish_update.py <version>")
    main(sys.argv[1])
