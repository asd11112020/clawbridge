#!/usr/bin/env python3
"""Push all remaining files to GitHub via Maton API.
Repo already has README.md from initial commit."""

import os, json, urllib.request

MATON_KEY = "v2.zCtQCQ0c-RWYSajt641x1kc45WHJR3CUpiG_hr8LCMdmF_Xwb4sFq6Dt7kTR-ky6ckWQOBe8VZRarxWWl-oVqNMYEM21Bt6BsXpljV6ND_1uzM9c9hOtFC8n"
OWNER = "asd11112020"
REPO = "clawbridge"
BASE = "https://api.maton.ai/github"
REPO_DIR = "/home/tiny/.openclaw/workspace/clawbridge"

def api(method, path, data=None):
    url = f"{BASE}{path}"
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Authorization", f"Bearer {MATON_KEY}")
    if body:
        req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

# Get current HEAD commit SHA (parent for our new commit)
ref = api("GET", f"/repos/{OWNER}/{REPO}/git/refs/heads/main")
parent_sha = ref["object"]["sha"]
print(f"Parent commit: {parent_sha[:7]}")

# Get all files except push_to_github.py and README.md (already pushed)
files = []
for root, dirs, fnames in os.walk(REPO_DIR):
    dirs[:] = [d for d in dirs if d != ".git"]
    for fname in fnames:
        if fname == "push_to_github.py":
            continue
        fpath = os.path.join(root, fname)
        rel = os.path.relpath(fpath, REPO_DIR)
        files.append((rel, fpath))

print(f"Found {len(files)} files to push")

# Create blobs
blobs = []
for rel_path, fpath in sorted(files):
    with open(fpath, "rb") as f:
        content = f.read()
    text = content.decode("utf-8")
    result = api("POST", f"/repos/{OWNER}/{REPO}/git/blobs", {
        "content": text,
        "encoding": "utf-8"
    })
    sha = result["sha"]
    blobs.append((rel_path, sha))
    print(f"  ✅ {rel_path} → {sha[:7]}")

# Create tree from all blobs (including new README.md blob)
tree_items = []
for rel_path, sha in blobs:
    tree_items.append({
        "path": rel_path,
        "mode": "100644",
        "type": "blob",
        "sha": sha
    })

tree_result = api("POST", f"/repos/{OWNER}/{REPO}/git/trees", {"tree": tree_items})
tree_sha = tree_result["sha"]
print(f"\nTree: {tree_sha[:7]} ({len(tree_items)} items)")

# Create commit with parent
commit_result = api("POST", f"/repos/{OWNER}/{REPO}/git/commits", {
    "message": "Add ClawBridge source code and build config",
    "tree": tree_sha,
    "parents": [parent_sha]
})
commit_sha = commit_result["sha"]
print(f"Commit: {commit_sha[:7]}")

# Update main branch
api("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/main", {
    "sha": commit_sha,
    "force": False
})
print(f"\n✅ All pushed! https://github.com/{OWNER}/{REPO}")
