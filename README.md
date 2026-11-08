# common-procedural

Procedural assets for true-sim, generated deterministically from parameters and a seed: trees first, then rocks,
plants, buildings and textures. Engine-agnostic: the generators depend on common-math only.

## Building

```bash
./gradlew build
```

Uses `../build-logic` and `../common-math` automatically when checked out next to this repository (override with
`-Ptruesim.<repo>=<path>`, or `false`), or fetches them from GitHub Packages (see build-logic's README for
credentials).

## Licence

MIT, see [LICENSE](LICENSE).
