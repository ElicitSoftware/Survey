#!/usr/bin/env python3
"""Claude Code PreToolUse hook: long-lived branches change only through pull requests.

Refuses `git commit`, `git cherry-pick` and `git revert` while the repository the command
acts on is on main, master, V2 or V3, and refuses any `git push` whose destination is one of
those branches, forced or not. Everything else passes through untouched.

The hook reads the Bash command Claude is about to run from stdin (the hook input JSON),
resolves which repository it targets (`git -C <dir>`, a preceding `cd <dir>`, or the
session's working directory, so `git -C Survey commit` from the umbrella is guarded too)
and asks that repository for its current branch. Exit 2 blocks the command and hands the
message on stderr back to Claude; exit 0 lets it run.

Parsing is deliberately shallow: the command is split into words, with `&&`, `||`, `;`,
`|`, `&`, parentheses and newlines as statement separators, and heredoc bodies removed
first so that prose in a commit message or an issue body is never read as shell. A `git`
word starts an invocation that runs to the next separator. Anything the parser cannot make
sense of is let through; the GitHub ruleset is the backstop.

This is one half of the guard. The other half is the GitHub ruleset on main and V3, which
binds humans and agents alike; this hook exists so that the agent never has to be told no
by the server. A human can let one command through by prefixing it with
ELICIT_ALLOW_PROTECTED=1; the agent must not.
"""

import json
import os
import re
import shlex
import subprocess
import sys

PROTECTED = {"main", "master", "V2", "V3"}
OVERRIDE = "ELICIT_ALLOW_PROTECTED"
SEPARATORS = {"&&", "||", ";", "|", "&", "(", ")", "\n"}
COMMIT_LIKE = {"commit", "cherry-pick", "revert"}
GIT_OPTS_WITH_ARG = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path"}
PUSH_OPTS_WITH_ARG = {"-o", "--push-option", "--receive-pack", "--exec", "--repo",
                      "--recurse-submodules", "--signed"}
# `<<EOF` or `<<-'EOF'` through the matching terminator line, body included. The rest of the
# line carrying the operator (a `)"` closing a $(cat ...) substitution, say) is kept.
HEREDOC = re.compile(r"<<-?\s*(['\"]?)(\w+)\1(?P<rest>[^\n]*)\n.*?\n[ \t]*\2(?=\n|$)", re.S)
HOW_TO = (
    "main, master, V2 and V3 only change through a pull request. Create a branch for the "
    "issue (`git switch -c <type>/<issue>-<slug>`), commit there, push it and open the PR. "
    "A human can let one command through by prefixing it with %s=1; do not do that yourself."
    % OVERRIDE
)


def strip_heredocs(command):
    return HEREDOC.sub(lambda m: m.group("rest"), command)


def tokens(command):
    command = strip_heredocs(command)
    lexer = shlex.shlex(command, posix=True, punctuation_chars="();<>|&\n")
    lexer.whitespace = " \t\r"
    lexer.whitespace_split = True
    lexer.commenters = ""
    try:
        return list(lexer)
    except ValueError:
        # Unbalanced quotes somewhere. Fall back to whitespace splitting per line, keeping
        # the line breaks as separators so a later line is never read as part of an earlier
        # git invocation.
        out = []
        for line in command.split("\n"):
            out.extend(line.split())
            out.append("\n")
        return out


def branch_of(path):
    try:
        done = subprocess.run(
            ["git", "-C", path, "symbolic-ref", "--short", "-q", "HEAD"],
            capture_output=True, text=True, timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    return done.stdout.strip() or None


def refuse(what, branch, path):
    sys.stderr.write("branch-guard: refusing `%s` on branch '%s' (%s). %s\n"
                     % (what, branch or "unknown", path, HOW_TO))
    sys.exit(2)


def destinations(args, branch):
    """Protected branches a `git push` with these arguments would write to."""
    positional = []
    force = delete = everything = False
    skip_next = False
    for arg in args:
        if skip_next:
            skip_next = False
            continue
        if arg in ("-f", "--force", "--force-if-includes") or arg.startswith("--force-with-lease"):
            force = True
        elif arg in ("-d", "--delete"):
            delete = True
        elif arg in ("--all", "--mirror", "--branches"):
            everything = True
        elif arg in PUSH_OPTS_WITH_ARG:
            skip_next = True
        elif arg.startswith("-"):
            continue
        else:
            positional.append(arg)
    refspecs = positional[1:]  # positional[0] is the remote
    targets = set()
    if everything:
        targets |= PROTECTED
    if not refspecs and branch:
        targets.add(branch)
    for spec in refspecs:
        spec = spec.lstrip("+")
        if spec.startswith(":"):
            dst = spec[1:]  # `push origin :branch` deletes it
        else:
            src, _, dst = spec.partition(":")
            dst = dst or src
        if dst == "HEAD" and branch:
            dst = branch
        if dst.startswith("refs/heads/"):
            dst = dst[len("refs/heads/"):]
        targets.add(dst)
    verb = "force-push" if force else "delete" if delete else "push"
    return targets & PROTECTED, verb


def resolve(base, path):
    return os.path.normpath(os.path.join(base, os.path.expanduser(path)))


def main():
    try:
        data = json.load(sys.stdin)
    except ValueError:
        return
    if data.get("tool_name") != "Bash":
        return
    command = (data.get("tool_input") or {}).get("command") or ""
    if "git" not in command or OVERRIDE + "=" in command:
        return
    cwd = data.get("cwd") or os.getcwd()

    toks = tokens(command)
    current_dir = cwd
    i = 0
    while i < len(toks):
        tok = toks[i]
        if tok == "cd" and i + 1 < len(toks) and toks[i + 1] not in SEPARATORS:
            current_dir = resolve(current_dir, toks[i + 1])
            i += 2
            continue
        if tok != "git" and not tok.endswith("/git"):
            i += 1
            continue
        # One git invocation: global options, subcommand, arguments up to a separator.
        repo_dir = current_dir
        j = i + 1
        while j < len(toks) and toks[j].startswith("-") and toks[j] not in SEPARATORS:
            opt = toks[j]
            if opt in GIT_OPTS_WITH_ARG and j + 1 < len(toks):
                if opt == "-C":
                    repo_dir = resolve(repo_dir, toks[j + 1])
                j += 2
            elif opt.startswith("-C") and len(opt) > 2:
                repo_dir = resolve(repo_dir, opt[2:])
                j += 1
            else:
                j += 1
        if j >= len(toks) or toks[j] in SEPARATORS:
            i = j
            continue
        sub = toks[j]
        k = j + 1
        args = []
        while k < len(toks) and toks[k] not in SEPARATORS:
            args.append(toks[k])
            k += 1
        if sub in COMMIT_LIKE:
            branch = branch_of(repo_dir)
            if branch in PROTECTED:
                refuse("git " + sub, branch, repo_dir)
        elif sub == "push":
            branch = branch_of(repo_dir)
            hit, verb = destinations(args, branch)
            if hit:
                refuse("git %s to %s" % (verb, ", ".join(sorted(hit))), branch, repo_dir)
        i = k


if __name__ == "__main__":
    main()
