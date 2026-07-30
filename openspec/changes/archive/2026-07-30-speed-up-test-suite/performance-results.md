## Test-suite performance results

### Current-version warm-image runs

All runs used `./scripts/measure-test-suite.sh` on the same host. Detailed JSON
and Maven logs are under `target/test-performance/`.

| Run | Suites | Tests | Wall seconds | Aggregate seconds | Contexts | App PostgreSQL | Fresh PostgreSQL | App Neo4j |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `current-1` | 111 | 494 | 60 | 54.539 | 16 | 1 | 4 | 1 |
| `current-2` | 111 | 494 | 61 | 55.391 | 16 | 1 | 4 | 1 |
| `current-3` | 111 | 494 | 61 | 55.179 | 16 | 1 | 4 | 1 |

The current-version median wall time is 61 seconds and median aggregate test
time is 55.179 seconds. Every run executed the same 111 suites and 494 tests,
with 16 Spring context starts, one shared application PostgreSQL start, four
intentionally independent fresh PostgreSQL starts, and one shared application
Neo4j start. The current inventory includes two fast/full classification
regression tests and one shared-container lifecycle regression test added by
this change.

Per user direction, the previous revision was not rerun and no reconstructed
historical run artifacts are included.
