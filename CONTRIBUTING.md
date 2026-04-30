# Contributing

Thanks for contributing.

## Development setup

1. Use JDK 25.
2. Build and test with:

```bash
mvn verify
```

## Before opening a pull request

1. Make sure the full reactor passes.
2. Keep module boundaries intact.
3. Prefer low-allocation and low-copy paths on hot request/response flows.
4. Add or update tests for behavioral changes.
5. Update README or module docs if public behavior changed.

## Versioning

- Development normally happens on a `*-SNAPSHOT` version in the root `pom.xml`.

## Pull requests

- Keep PRs focused.
- Include a short summary of user-visible changes.
- Call out any compatibility or wire-protocol impact explicitly.