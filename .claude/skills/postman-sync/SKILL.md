---
name: postman-sync
description: Update the "URL Shortener" Postman collection after HTTP endpoints change. Diffs the controllers in the code against the collection in Postman via the Postman MCP connector, proposes adds/changes, and applies them only after the owner approves. Use when the owner asks to update/sync Postman, or after adding or changing an endpoint, status code, validation rule, or metric.
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
6. **Report.** List what changed in Postman (request names and ids), and mention anything the connector couldn't do.
   The Postman connector can't run collections, and its cloud runner can't reach localhost. Don't claim any run results. Tell the owner to run the collection in the Postman app against the local stack (`make up`), and report only results they paste back.
   Don't commit unless asked. If asked, follow `.agents/skills/git-conventions/SKILL.md`.
