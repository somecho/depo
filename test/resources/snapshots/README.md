This directory contains snapshots created for the end-to-end tests. The
snapshots have to be updated regularly to keep the tests up-to-date.

### Directory structure
- update-all - snapshots of the `update` command

### Generating snapshots
```bash
clj -T:generate all-snapshots
```
