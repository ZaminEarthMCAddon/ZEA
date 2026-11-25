# The ZEA ecosystem

How ZEA's repositories map onto the NotEnoughUpdates ecosystem they are
modeled on — what was studied, what maps where, and what is deliberately
not built. This is a decision record, not an aspiration list: every
"later" below names the condition that would move it to "now".

## What was studied

The upstream organization ([NotEnoughUpdates](https://github.com/NotEnoughUpdates))
splits one mod into a working set of companion repositories, each with a
narrow job:

| Upstream repository | State | Job |
|---|---|---|
| NotEnoughUpdates | archived | The 1.8.9 Forge mod — the item browser's reference implementation. |
| NotEnoughUpdates-REPO | active | The community data repository the mod consumes. |
| NEU-REPO-Workflow | active | The data-processing tool, built by CI and published to a `dist` branch for the data repository's workflows to run. |
| NotEnoughUpdates-REPO-Workflow | archived | The superseded predecessor of the above — studied for its migration lessons, not reused. |
| MoulConfig | active | The configuration framework the mod builds its settings UI on. |
| RepoParser | active | A standalone library that parses the data repository. |
| schemas | active | The formal data contracts the mod, repo and tooling share. |
| NEU-Lints | active | Shared code-quality automation. |
| NotEnoughUpdates-Docs / website / .github.io | active / historical | Documentation and web presence. |
| Hychat, Button-Presets | active | The chat and inventory-button references earlier ZEA work rebuilt on. |

The load-bearing lesson is how the pieces stay consistent: the schemas are
the contract, the repository's CI validates every contribution against
them, the workflow tool consumes the same contracts, and the mod reads the
validated output through a manifest with content hashes — so no two
implementations of the data format can drift apart silently.

## The mapping

| ZEA repository | Maps to | Why it exists |
|---|---|---|
| [ZEA](https://github.com/ZaminEarthMCAddon/ZEA) | NotEnoughUpdates | The user-facing Fabric mod: chat, the NEU-style item browser, shop observation, rarity. |
| [ZEA-REPO](https://github.com/ZaminEarthMCAddon/ZEA-REPO) | NotEnoughUpdates-REPO | Community data: curated datasets, JSON schemas, and the CI that validates every push and pull request. |

ZEA-REPO's CI (`.github/workflows/validate.yml`) is the workflow layer's
first slice: `scripts/validate.py` validates every dataset against its
schema, rejects malformed records, and regenerates `manifest.json` — the
CI-generated index with SHA-256 per file that the mod's sync
(`RepoSync`) uses as its change detector, conditional-GET tag and cache
key. That is the same manifest-and-hash contract NEU's mod and repo
share, already wired end to end.

## Deliberately not built (yet)

- **ZEA-Schemas** — the schemas live in ZEA-REPO beside the data they
  validate, versioned by the manifest's `version` field. A separate
  contract repository pays off when a second consumer needs to version
  against the schemas independently of the data; one consumer does not.
  Move the schemas out when the workflow tool or a parser library first
  consumes them directly.
- **ZEA-REPO-Workflow** — NEU extracts its data tool into its own
  repository because the tool is a Java distribution consumed through a
  `dist` branch. ZEA's validation is a single Python script the data
  repository runs itself; a second repository would host one file. Extract
  it when the validation grows beyond one script or a second repository
  needs to reuse it as a reusable GitHub Action.
- **ZEA-REPO-Parser** — the mod's `DatasetMerger` and `SharedShops` parse
  the dataset client-side; a standalone parser library would be a second
  implementation of the same format and drift against the mod's, the
  exact failure mode NEU's RepoParser accepts cost for. Build one when an
  external tool (not ZEA itself) needs to read the dataset.
- **ZEA-Docs / website** — the repositories' READMEs and `docs/` are the
  documentation today. A dedicated site pays off when the guides outgrow
  a README.

## The data flow

```
EarthMC server
      |  (shop sign inspection, server responses)
      v
ZEA client observation — exact ItemStack fingerprint + physical shop
      v
local persistence (market store, schema v2)
      |
      +--> validated submission queue --> ZEA-REPO pull request
      |                                        |  CI: schema validation,
      |                                        |      manifest regeneration
      |                                        v
      +--> RepoSync <-------------------- published manifest.json
             (conditional GET, SHA-256 gate, atomic cache swap)
                   v
             DatasetMerger — shared records never overwrite newer
             verified local observations
                   v
             the item browser, the tooltips, the shop waypoints
```

The flow is implemented end to end: observation, fingerprinting,
persistence, the submission queue, the repository's validation CI, the
manifest, the sync, and the merge — each piece carries its own tests in
the ZEA repository, and the repository's CI enforces the contract on
every contribution.
