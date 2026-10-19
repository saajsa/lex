# Documentation

Developer reference for the Legum Lex Android client. Start here.

| Document | Read it for |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | The big picture — tech stack, package layout, the feature-module / MVVM pattern, navigation, networking, design system, feature inventory, conventions. |
| [API.md](API.md) | The `customers_api` backend — base URLs, auth scheme, response envelope, the full endpoint catalogue, and the routing caveat. |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Day-to-day workflow — build & run, the Cloudflare tunnel setup, build variants, Google OAuth, release signing, deploying backend changes, gotchas, roadmap. |

**New to the project?** Read `ARCHITECTURE.md` end to end, then skim
`DEVELOPMENT.md` §3 (the tunnel) before your first debug run.

Keep these files current — when you change structure, navigation, or endpoints,
update the matching doc in the same commit.
