# Project workflow

- The initial Git commit records the existing Picaris 0.2.0 baseline. Do not invent earlier commits or backdate history.
- Commit subsequent independent features and fixes promptly, with a message describing the actual change. Avoid mixing unrelated work.
- Before committing, inspect the staged file list and diff for credentials, account data, local settings, SDKs, caches, and generated artifacts. Keep these out of Git.
- Keep build scripts, the Gradle Wrapper, source, tests, profiles, and license notices under version control.
- Do not clear the authenticated main app's data or perform real-account write actions merely to validate a change. Use the isolated QA package when appropriate.
- Keep verification proportional to the change. Do not rerun application tests for Git metadata, ignore rules, or delivery documentation changes.
- At delivery, report the commit hash and working-tree status. Save delivery artifacts under outputs/, which is intentionally ignored.
