This directory contains snapshots created for the end-to-end tests. The
snapshots have to be updated regularly to keep the tests up-to-date. Snapshots
are [generated programmatically](../../../generate.clj).

### Generating snapshots
```bash
clj -T:generate all-snapshots
```
