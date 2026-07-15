# Datapack Formats

All of Thaumaturgy's content is datapack-driven and reloads with `/reload`.
Namespaced under your pack; examples use `new_age_thaum`.

## Aspects — `data/<ns>/aspects/<name>.json`

The file name is the aspect id. See [aspects.md](aspects.md) for the canonical set.

```json
{ "color": "#F0552B", "components": ["new_age_thaum:flamma", "new_age_thaum:ventus"] }
```

- `color` — hex string, required.
- `components` — zero (primal) or exactly two aspect ids (compound). Components
  must exist and must not form a cycle; invalid aspects are dropped with a log
  warning rather than crashing the reload.

## Aspect assignments — `data/<ns>/aspect_assignments/<name>.json`

Assigns aspect bags to items or item tags. Multiple files merge; several tag
matches on one item take the per-aspect maximum.

```json
{
  "entries": [
    { "matches": ["#minecraft:logs"], "aspects": { "new_age_thaum:silva": 3, "new_age_thaum:flora": 1 } },
    { "matches": ["minecraft:diamond"], "aspects": { "new_age_thaum:gemma": 4 } }
  ]
}
```

- `matches` — a list of item ids and/or `#tag` references.
- `aspects` — aspect id → amount (1–65536).
- An **empty** `aspects` object (`{}`) explicitly opts an item out of both
  assignment and recipe inference.

### Recipe inference

Items with no explicit assignment derive aspects from their crafting recipes:
the summed ingredient aspects are dampened per craft step and split across the
recipe's output count, so processed items are weaker than their inputs. Provide
an explicit assignment to override inference for any item.

## Codex entries — `data/<ns>/codex_entries/<name>.json`

The file name is the entry id.

```json
{ "category": "fundamenta", "title": "codex.new_age_thaum.entry.welcome", "icon": "new_age_thaum:codex", "x": 0, "y": 0 }
```

- `category` — category tab the entry appears under.
- `title` — a translation key for the entry title.
- `icon` — item id shown as the node icon (defaults to `minecraft:book`).
- `x`, `y` — grid position within the category.

Research gating and entry pages arrive in a later milestone.
