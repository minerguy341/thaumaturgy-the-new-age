---
name: parallel-session-survey
description: >
  Survey other people's / other sessions' in-flight work on a shared repo BEFORE you build a
  feature and BEFORE you land a long-lived branch, so you don't independently reimplement a
  system that already exists on another branch. Use this at the START of substantial feature
  work on any repo where parallel sessions/branches are the norm (this project: sessions share
  a branch name across both repos), and again just BEFORE merging. It ranks branches by most
  recent activity and tells you how to adopt-vs-rebuild when two branches grow the same system.
  Consult it whenever you're about to add a new subsystem, data component, menu, or store — the
  cheap catch is at start, not at merge.
---

# Parallel-session survey

## Why this exists

This workflow is structurally multi-session: sessions run in parallel on different branches of
the same repo (both repos here even share one branch name across sessions). Two sessions can
independently build the SAME system with no textual merge conflict — a clean `git merge` hides
it, and you only discover it after the fact.

Real case (2026-07): the Arcane Worktable branch built an `AspectBag`-based wand-vis system while
`main` independently grew a per-primal `WandVis` float record + `WandRecharge`. Nothing conflicted
textually; both couldn't coexist. Caught only at merge time → a full reconciliation rewrite of the
menu + screen + gametest. The same session's Orrery redesign was *also* a parallel session. The
survey below would have caught it at START (adopt main's `WandVis` up front, write zero throwaway
code) instead of at merge.

**The expensive collision is the SILENT one** — two implementations of one system, no merge marker.
A textual conflict is cheap; git shows it to you. This survey targets the semantic collision git
can't see.

## When to run it

1. **Pre-flight** — before writing a new subsystem / data component / menu / SavedData / store.
   The cheapest fix is to adopt what already exists before you write anything.
2. **Pre-land** — before merging your feature branch, even if `git merge` reports no conflict.

## Pre-flight survey

Run `get_me` first (context/permissions), then:

1. **Open PRs, most-recently-updated first** — the strongest "who is working NOW" signal:
   `list_pull_requests(state="open", sort="updated", direction="desc")`. Read the titles/branches;
   skim the diff of any that touches your area (`pull_request_read`).
2. **Recently-merged work on the default branch** — a system that already merged is not a live
   collision, it's the base you build on:
   `list_commits(sha="main", perPage=20)` and read the messages for your area.
3. **Rank ALL branches by recency (the key step).** `list_branches` returns only names + head
   SHAs — NO dates — so it can't rank on its own. To find the most-recently-worked branches:
   - `list_branches(perPage=100)` to enumerate.
   - Drop obvious stale/merged ones by name; for the remaining candidates probe each branch's head
     commit date with `list_commits(sha="<branch>", perPage=1)` → read `commit.author.date` (or
     `commit.committer.date`). One call per branch; that date IS "last worked on."
   - Sort those dates descending. The top few branches are where live parallel work is happening —
     read their recent commits/diffs in your area FIRST; they're the likeliest to collide with what
     you're about to build.
   - Shortcut: `search_commits(query="repo:OWNER/REPO <system-name>", sort="committer-date")` finds
     branches that recently touched a named system without probing every branch.
4. **Decide before writing.** If a branch or `main` already has the system you were going to build,
   adopt THAT API as your base. Building your own parallel version is the throwaway path.

## Pre-land diff

Even when `git merge` reports no conflict:

1. `git fetch origin main` and diff the areas your branch touched against current `main`
   (`git diff origin/main...HEAD -- <paths>`), and re-run the recency ranking above — new branches
   may have landed since pre-flight.
2. If you find a competing implementation of a system you touched, **adopt the one on `main` as the
   base and rewrite your feature onto its API.** `main` is shared ground; your branch is not. Don't
   force your parallel version through.

## Collision triage

| Signal | Cost | Action |
|---|---|---|
| Textual merge conflict | cheap | git shows it — resolve normally |
| Two impls of one system, **no** conflict marker | expensive | adopt main's/the-landed API, rewrite yours onto it |
| Your system already merged to main | — | not a collision; rebase onto it, don't re-add |

## Limitation — this only sees pushed work

The survey ranks **landed and pushed** branches/PRs. A parallel session that is mid-flight but
hasn't pushed is invisible to every tool here. This reduces collision risk; it does not eliminate
it. When you're about to build something central (a shared store, a core data component), it's worth
asking the user whether another session is already on it — the tools can't tell you.

## Cross-repo note

The same collision risk applies in the Pixel-Art-Aide repo (parallel sessions, shared branch name).
The procedure is identical there; only the "systems to watch" differ (style cards, analyzer
thresholds, the `aide` toolkit API instead of menus/components).
