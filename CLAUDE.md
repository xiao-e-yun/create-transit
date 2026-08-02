# create_transit

Respond in 繁體中文 (zh-TW).

## Upstream source lookup

Never guess Create / Flywheel / NeoForge APIs from memory — read the real source with `gh`.
The repos and their branches for THIS branch (NeoForge 1.21.1) are in the Upstream sources
table in [README.md](README.md); the Forge 1.20.1 line lives on `main` with its own table.

**Always pass `?ref=`.** An upstream repo's default branch is usually some other Minecraft
version; reading it yields APIs that are silently wrong here.

```bash
gh api "repos/OWNER/REPO/contents/PATH?ref=BRANCH" --jq '.[].name'                       # list a dir
gh api "repos/OWNER/REPO/contents/PATH/File.java?ref=BRANCH" --jq '.content' | base64 -d  # read a file
gh search code "SymbolName" --repo OWNER/REPO --limit 10                                 # locate a path
```

`gh search code` only indexes the default branch. Use it to find where a symbol lives,
then read the actual content from the right branch with `gh api ... ?ref=`.
