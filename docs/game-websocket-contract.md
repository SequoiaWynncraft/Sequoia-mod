# Game WebSocket contract fixtures

`src/test/resources/contracts/game-websocket-v1.json` is vendored from the
sequoia-backend repository. The backend owns the fixture and the maintenance
instructions in `docs/game-websocket-contract.md`.

`GameWebSocketContractTest` runs these fixtures through the production guild-chat
payload builder and Discord-chat dispatcher. The normal Gradle `test`/`build`
tasks run it without requiring a backend checkout or service.

Guild-chat messages allow 400 Java UTF-16 units after `String.trim()`. The fixture
covers accepted and rejected boundaries, required fields, legacy optional fields,
item-preview sections and additive fields. It pins canonical outgoing payloads,
not just a duplicate length constant.

When changing the wire contract, update the backend fixture and copy it here.
Run both repositories' contract tests, then compare the copies from the backend:

```sh
python3 scripts/check-websocket-contracts.py ../Sequoia-mod
```

Each repository's CI checks its own vendored fixture. The explicit comparison
detects cross-repository copy drift; it does not run automatically against the
other repository's remote HEAD.
