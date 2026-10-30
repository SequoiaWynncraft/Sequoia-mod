# Game WebSocket contract fixtures

`src/test/resources/contracts/game-websocket-v1.json` comes from
[`sequoia-backend`](https://github.com/SequoiaWynncraft/sequoia-backend).
The backend owns the fixture and its maintenance instructions.

`GameWebSocketContractTest` runs these fixtures through the production guild-chat
payload builder and Discord-chat dispatcher. `mise run test` runs it without
a backend checkout or service.

Guild-chat messages allow 400 Java UTF-16 units after `String.trim()`. The fixture
covers accepted and rejected boundaries, required fields, legacy optional fields,
item-preview sections and additive fields. It pins canonical outgoing payloads,
not just a duplicate length constant.

When changing the wire contract, update the backend fixture and copy it here.
Run both repositories' contract tests, then compare the copies using the
backend's `scripts/check-websocket-contracts.py`.
See the backend's `docs/game-websocket-contract.md` for that workflow.

Each repository's CI checks its own vendored fixture. The explicit comparison
detects cross-repository copy drift; it does not run automatically against the
other repository's remote HEAD. See [development](development.md) for this
repository's tasks.
