# create_transit

## Upstream source lookup

Never guess Create / Flywheel / Forge APIs from memory — read the real source with `gh`.
The repos and their 1.20.1 branches are in the Upstream sources table in [README.md](README.md).

**Always pass `?ref=`.** Every upstream repo's default branch is a newer Minecraft version
(Create's `master` is NeoForge 1.21); reading it yields APIs that are silently wrong here.

```bash
gh api "repos/OWNER/REPO/contents/PATH?ref=BRANCH" --jq '.[].name'                       # list a dir
gh api "repos/OWNER/REPO/contents/PATH/File.java?ref=BRANCH" --jq '.content' | base64 -d  # read a file
gh search code "SymbolName" --repo OWNER/REPO --limit 10                                 # locate a path
```

`gh search code` only indexes the default branch. Use it to find where a symbol lives,
then read the actual content from the 1.20.1 branch with `gh api ... ?ref=`.
