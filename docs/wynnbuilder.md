# WynnBuilder Integration

Sequoia embeds an in-game version of [WynnBuilder](https://wynnbuilder.github.io) so builds and
crafts can be read and written without leaving the client. Compatibility with the website's links is
the point of the feature, so most of the design follows from the link format.

## Licensing and data

WynnBuilder is GPL-3 licensed; Sequoia is MIT. The implementation therefore works from the published
link specification (`ENCODING.md` and the upstream developer page) and from Wynncraft's own game
mechanics. No upstream source is transcribed, and no upstream file is redistributed in the jar.

Data files are downloaded at runtime from `wynnbuilder.github.io/data/<version>/` and cached under
`config/sequoia/wynnbuilder/<version>/`. `WynnDataRepository` fetches them off the render thread,
writes each file through a temporary path so an interrupted download cannot masquerade as a valid
cache entry, and evicts the oldest versions once more than three are held in memory.

## Package layout

```
wynnbuilder/
  codec/   bit-level link encoding and decoding
  data/    downloaded data, parsed and indexed
  calc/    skill points, rolls, powders, aggregated stats, crafting
  atree/   ability tree graph, selection rules and effect evaluation
  live/    the build the player is actually wearing, and its audit
  ui/      the hub, builder, crafter, ability tree and picker screens
```

`WynnBuilderSession` is the single piece of shared state: the working build, the craft, the data set
in use, and the cached statistics. Screens read from it and mark it dirty; nothing else caches.

## The link format

Two encodings are in circulation, and both are supported for reading.

**Binary (version 12)** is what the site writes today. The hash is a bit vector rendered six bits per
Base64 character, holding a header, the nine equipment slots with their powders, tomes, skill
points, the level, aspects, and finally the ability tree.

Three details are easy to get wrong and are pinned by tests:

- **Bit order is least-significant-first.** Upstream stores bit *i* at `1 << (i % 32)` and reads a
  field by right-shifting, so the earliest bit of a field or a character is its lowest one. A
  most-significant-first vector produces hashes the site silently rejects.
- **The version field is an index**, not a version string. It points into an ordered list of data
  versions. `WynnDataVersions` keeps a built-in copy and can extend it from the upstream directory
  listing, because that list is exactly the numeric directories under `data/` in ascending numeric
  order — so a new upstream version stays readable without a mod update.
- **Crafted padding is always one to six bits**, never zero. An already-aligned craft still gets six
  bits of padding, and skipping a "smarter" amount desynchronises the stream.

Aspects also carry a tier alongside their ID, which the published specification omits.

**Legacy (versions 0 to 11)** are fixed-width Base64 fields rather than a bit stream. They are
decode-only: new links are always written in the binary format. Support matters because most links
shared over the years use these formats — the upstream regression corpus is entirely legacy.

## Correctness

The codec is the part where a mistake is invisible until someone opens a broken link, so it carries
the most testing:

- Every one of the 160 hashes in the upstream regression corpus decodes, with the field offsets
  cross-checked against an independent derivation.
- Builds survive encode, decode and re-encode to a byte-identical hash, including the powder
  repeat-flag optimisations, negative skill points, crafted items embedded in a slot, and items with
  ID 0 (a real item, distinct from an empty slot).
- Bit ordering and the Base64 table are asserted directly rather than only through round-trips,
  which would pass just as happily with both ends reversed.

Test fixtures are hand-written in the upstream schema rather than copied from the WynnBuilder
repository.

## Statistics

`BuildStats` aggregates items, tomes, powders and ability tree bonuses. Two parts are less obvious
than they look:

- **Roll ranges are ordered by quality, not by number.** `IdentificationRolls.Range` names its ends
  `worst` and `best`, because for a drawback the best roll is the numerically larger one, and for a
  spell cost reduction it is the more negative one.
- **Skill point allocation is an optimisation, not a sum.** Items grant skill points as well as
  requiring them, so the order pieces are equipped changes how many points must be assigned.
  `SkillPoints.allocate` searches the equip orders for the cheapest valid one; an item cannot pay for
  its own requirement, and crafted items' bonuses do not count towards requirements at all.

## The equipped build

The same calculator, pointed at the player instead of at a hand-entered build. Only the input is
new: `BuildEvaluation` is shared with the builder screen precisely so the two cannot drift, since
the damage numbers are the whole point of both.

### Reading a worn item

`BuildEquipment.Live` is a slot holding an already-resolved item rather than a reference into the
data set. Its identifications are the roll that particular drop got, and it is marked `fixID` so
`IdentificationRolls` returns them verbatim instead of re-rolling numbers that are no longer
hypothetical.

Two details are worth knowing.

**Powder tiers are unknowable and do not need to be known.** Wynncraft never prints a powder's tier
— Wynntils hardcodes six when it encodes an item — so applying `PowderCalc` to an item's base stats
would mis-state every powdered weapon. `WynnItemParser.parseItemStack` returns the tooltip's own
damage, health and defences, which already have whatever tier is really socketed applied. A live
piece therefore carries powdered stats and an empty powder list, and `PowderCalc` is bypassed rather
than fed a guess.

**Wynntils and WynnBuilder disagree on the sign of a spell cost.** Wynntils negates cost stats while
parsing so that a larger number is always the better roll; the calculator keeps the game's own sign,
where a reduction is the negative number the tooltip shows. `StatKeys.isNegated` is the flip, and
missing it would turn every cost reduction into a cost increase.

### Where the rest of a character comes from

Gear is always readable. Everything else is printed only inside a menu, and all three are read while
the player has that menu open rather than by asking the server for it: `AbilityTreeParser` is public,
so the parsing Wynntils does during its own query works just as well on a container the player
opened. The exception is skill points, which appear only in the character sheet, and there is no
passive path — the panel says they are missing until a scan is asked for.

The ability tree scrolls across seven pages, so a passive read is often partial. Observations
accumulate by ability name across openings and the coverage is reported, because the alternative —
treating an unobserved node as taken — would overstate the build instead of visibly understating it.

### The audit

`GearAudit` answers "what do I replace first" by measuring rather than scoring. For each slot it
re-runs the whole pipeline with the piece at its ceiling and with the piece removed, and reports the
difference in the build's own damage. Nothing in it knows what a good item looks like, which is
exactly why it survives a build whose damage scales off health.

Two things make the numbers comparable:

- **One reference source.** The strongest sustained output is chosen once from the baseline and every
  variant is measured against that same spell. A delta between two different spells is not a delta.
- **A ceiling that depends on provenance.** A dropped item's is its best roll; a crafted item's also
  drops the identifications that hurt, since an ingredient that subtracts from the build is a choice
  and not a bad roll. Applying that to dropped gear would invent an impossible ideal and condemn
  every mythic for the drawback printed on it.

Nineteen full evaluations, so it runs off the render thread on request rather than on a frame.

## Ability tree

`atree.json` uses only four effect kinds — `raw_stat`, `stat_scaling`, `replace_spell` and
`add_spell_prop` — which is what makes full evaluation tractable.

Child order is load-bearing: the encoding walks children depth-first, one bit each, so a parent's
children must be visited in raw data-file order. A selection must also stay connected to the root,
which is why deselecting a node cascades to everything that depended on it — a disconnected
selection cannot be represented in a link at all.
