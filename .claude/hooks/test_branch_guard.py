#!/usr/bin/env python3
"""Exercises branch-guard.py against command shapes; exits non-zero if any case disagrees."""
import json, os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
GUARD = os.path.join(HERE, "branch-guard.py")
T = tempfile.mkdtemp(prefix="guardtest")
REPO, FEAT = os.path.join(T, "repo"), os.path.join(T, "feat")


def sh(*a, **k):
    subprocess.run(a, check=True, capture_output=True, **k)


sh("git", "init", "-q", "-b", "V3", REPO)
sh("git", "-C", REPO, "commit", "-q", "--allow-empty", "-m", "init")
sh("git", "-C", REPO, "branch", "feature/1-x")
sh("git", "init", "-q", "-b", "feature/2-y", FEAT)
sh("git", "-C", FEAT, "commit", "-q", "--allow-empty", "-m", "init")

failures = 0


def case(desc, cwd, command, want, tool="Bash"):
    global failures
    payload = json.dumps({"tool_name": tool, "cwd": cwd, "tool_input": {"command": command}})
    done = subprocess.run([sys.executable, GUARD], input=payload, capture_output=True, text=True)
    ok = done.returncode == want
    failures += 0 if ok else 1
    print("%s [%d want %d] %s%s" % ("ok  " if ok else "FAIL", done.returncode, want, desc,
                                     "" if ok else " :: " + done.stderr.strip()))


HEREDOC_FEAT = 'git commit -m "$(cat <<\'EOF\'\nTitle\n\nBody with # hash, "quotes" and a developer\'s apostrophe\nEOF\n)"'
UNQUOTED_HEREDOC = ("cat > body.md <<'EOF'\nA developer's checkout. Author's tests.\nEOF\n"
                    "git -C feat push -u origin fix/9-x && gh pr create --base main --head fix/9-x")
MULTILINE = ("set -e\ngit -C feat add -A\ngit -C feat commit -q -F msg\n"
             "git -C feat push -q -u origin fix/9-x\ngh pr create -R o/r --base main --head fix/9-x")

case("commit on V3", REPO, 'git commit -m "x"', 2)
case("commit on feature", FEAT, 'git commit -m "x"', 0)
case("git -C into V3 repo", T, "git -C repo commit -am x", 2)
case("cd then commit", T, "cd repo && git add . && git commit -m x", 2)
case("cd then commit on feature", T, "cd feat && git commit -m x", 0)
case("push with no refspec on V3", REPO, "git push", 2)
case("push -u origin feature", REPO, "git push -u origin feature/1-x", 0)
case("push origin V3 from feature", FEAT, "git push origin V3", 2)
case("push HEAD:V3", FEAT, "git push origin HEAD:V3", 2)
case("push HEAD:refs/heads/main", FEAT, "git push origin HEAD:refs/heads/main", 2)
case("force push feature", FEAT, "git push --force-with-lease origin feature/2-y", 0)
case("force push main", FEAT, "git push -f origin main", 2)
case("delete V3", FEAT, "git push origin --delete V3", 2)
case("push :V3", FEAT, "git push origin :V3", 2)
case("push --all", FEAT, "git push --all origin", 2)
case("status on V3", REPO, "git status && git log --oneline -3", 0)
case("pull on V3", REPO, "git pull --ff-only", 0)
case("merge origin/V3 on V3", REPO, "git merge origin/V3", 0)
case("cherry-pick on V3", REPO, "git cherry-pick abc123", 2)
case("heredoc commit on feature, apostrophe in body", FEAT, HEREDOC_FEAT, 0)
case("heredoc commit on V3", REPO, 'git commit -m "$(cat <<\'EOF\'\nTitle\n\nBody\nEOF\n)"', 2)
case("newline then commit on V3", T, "cd repo\ngit commit -m x", 2)
case("push feature then gh --base main on next line", T, "git -C feat push -u origin fix/9-x\ngh pr create --base main", 0)
case("unquoted heredoc with apostrophe then gh --base main", T, UNQUOTED_HEREDOC, 0)
case("multi-line script pushing a fix branch", T, MULTILINE, 0)
case("multi-line script pushing main", T, MULTILINE.replace("fix/9-x", "main"), 2)
case("push to unknown dir is let through", T, "git -C nowhere push -u origin fix/9-x", 0)
case("push HEAD:main from unknown dir still refused", T, "git -C nowhere push origin HEAD:main", 2)
case("override", REPO, "ELICIT_ALLOW_PROTECTED=1 git commit -m x", 0)
case("gh pr merge", REPO, "gh pr merge 5 --merge", 0)
case("echo git", REPO, "echo git", 0)
case("worktree add from origin/main", T, "git -C feat worktree add -q -b fix/9-x /tmp/x origin/main", 0)
case("non-Bash tool", REPO, "git commit", 0, tool="Read")
case("unbalanced quote on commit line, V3", REPO, "git commit -m \"it's", 2)

shutil.rmtree(T)
print("%d failure(s)" % failures)
sys.exit(1 if failures else 0)
