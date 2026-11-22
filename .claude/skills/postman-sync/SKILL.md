---
name: postman-sync
description: Update the "URL Shortener" Postman collection after HTTP endpoints change. Diffs the controllers in the code against the collection in Postman via the Postman MCP connector, proposes adds/changes, applies them only after the owner approves, then runs the collection against the local stack with the Postman CLI. Use when the owner asks to update/sync Postman, or after adding or changing an endpoint, status code, validation rule, or metric.
---

# postman-sync

The Postman workspace is the only copy of the collection (there are no Postman JSON files in the repo).

## Targets (local only)

| Item | Name | ID (uid) |
|---|---|---|
| Workspace | My Workspace (personal) | `de03d0bd-7055-423d-8dc0-8ff6f9ae3ae1` |
| Collection | URL Shortener | `27398777-8ba1d628-b1e0-49a4-827b-fff35adda56f` |
| Environment | URL Shortener - Local | `27398777-41ddc57f-6812-4517-9a8e-322f892347a4` |

Environment variables: `baseUrl=http://localhost:8080`, `prometheusUrl=http://localhost:9090`,
`grafanaUrl=http://localhost:3000`, `shortCode` and `shortUrl` empty (set by `Create link`).
If any ID above no longer resolves, list workspaces and collections, show the owner what you found, and ask. Don't recreate anything on your own.

## Steps

1. **Read the code (the source of truth for behaviour).**
   - Endpoints: `grep -rn "@\(Get\|Post\|Put\|Patch\|Delete\|Request\)Mapping" src/main/java`.
   - Statuses and error bodies: `@ResponseStatus`, `*ExceptionHandler.java`, bean-validation annotations on request records.
   - Metrics: the `Counter`/`Timer` names in services, plus `management.endpoints.web.exposure` in `application.yml`.
   - Use `git log --oneline -- src/main/java` to see what changed recently.
2. **Read Postman.** `getCollection` with `model=full` on the collection uid, and `getEnvironment`.
3. **Diff and propose.** Make a table: request | folder | change (add / modify / stale) | why (cite `file:line`).
   Folders: `1. Links`, `2. Redirect`, `3. Validation errors`, `4. Observability (app)`, `5. Infra (Prometheus / Grafana)`. Add a new folder only if a new feature package needs one.
   Every new request gets a test script that checks the expected status, plus the body shape if it has one.
   **Wait for the owner's OK before writing anything.**
4. **Apply.**
   - Change an existing request: `updateCollectionRequest`. Use the bare request id and the bare collection id (no `27398777-` prefix). It works like PATCH, and scripts are merged by `listen` type.
   - Add a request: `createCollectionRequest` with `folder` = folder id from step 2. Pass `events` for the test script.
   - **Redirect-style requests need `protocolProfileBehavior.followRedirects=false`.** `createCollectionRequest` and `updateCollectionRequest` can't set it. Instead, build the full collection from the step 2 payload plus your change and send it with `putCollection`. Keep every existing request, script, and `followRedirects:false`.
   - **Never delete or rename** requests, folders, or environment variables without asking. This connector has no delete tool anyway. Mark stale requests in the proposal and ask the owner to delete them in the Postman app.
   - No secrets or credentials in requests or the environment. Local URLs only.
5. **Verify.** Run `getCollection` (`model=full`) again and confirm the change landed, including scripts and `followRedirects`.
6. **Run the collection locally with the Postman CLI.** The MCP connector can't run collections, so use the CLI.
   - **Check the CLI is ready:**
     - `~/.local/bin/postman --version` must work. If it's missing, ask the owner to install it themselves with `! curl -fsSL https://dl-cli.pstmn.io/install/linux64.sh | sh -s -- --prefix "$HOME/.local" --no-modify-path --skip agent-discovery-hints,agent-plugins --yes`.
     - If the run says you're not logged in, ask the owner to run `! ~/.local/bin/postman login`. Never handle the API key yourself.
   - **Check the stack is up:** `curl -s localhost:8080/actuator/health` must return `"status":"UP"`. If it doesn't, ask the owner to start it (`make up`). Don't run against a down stack.
   - **Run it** and save the output:
     ```bash
     ~/.local/bin/postman collection run 27398777-8ba1d628-b1e0-49a4-827b-fff35adda56f \
       -e 27398777-41ddc57f-6812-4517-9a8e-322f892347a4 2>&1 | tee <scratchpad>/postman-run.txt
     ```
     Requests that change data (e.g. `POST /api/v1/links`) create real rows in the local database.
   - **Report the real outcome:**
     - the summary table (requests, assertions executed and failed);
     - every failing assertion verbatim;
     - which requests have no test script, so they were not checked at all.
     Never round a failure into a pass. If the run couldn't happen, say so and don't claim results.
   - **Note:** the CLI uploads run results to Postman Cloud by default (localhost URLs and test data). Mention the link it prints.
7. **Report.** List what changed in Postman (request names and ids), the run result from step 6, and anything the connector couldn't do.
   Don't commit unless asked. If asked, follow `.agents/skills/git-conventions/SKILL.md`.
