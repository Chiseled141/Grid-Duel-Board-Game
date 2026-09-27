# Experimental Evaluation — what to run and how

The `bots` mode is the measurement tool. It starts N headless clients that
register generated accounts, pair up and play random legal games as fast as
the server allows, measuring:

* **per-move round-trip latency** — from sending `MoveRequest` to receiving
  the matching `MoveApplied` (client-observed, includes the server's
  validation and broadcast);
* **throughput** — completed games per minute;
* the run's wall-clock time and a failure count.

Every run appends one row to `results/summary.csv` and writes the raw
per-move samples to `results/latencies.csv`. **No numbers in the report may
be invented — run the experiments below and copy the measured values.**

## Environment

* Machine: TODO(team) — record CPU model, core count, RAM, OS.
* JDK: `java -version` output. Maven only needed for building.
* Build: `mvn clean package` at the exact commit being measured.
* For each configuration: start a fresh server process
  (`rm -rf data/ results/` for a clean database), run one bot fleet, stop the
  server. Repeat 3 times per configuration and report the median.

## Experiments

### E1 — Latency and throughput vs. number of concurrent games

Keep total games per pair fixed; increase concurrency.

```bash
java -jar target/onitama.jar server --port 5555 &
for N in 2 4 10 20; do
  java -jar target/onitama.jar bots --host 127.0.0.1 --port 5555 \
       --bots $N --games 5
  rm -rf data/          # fresh DB per configuration
done
```

Record into this table (values from `results/summary.csv`):

| Bots (pairs) | games completed | games/min | mean RTT (ms) | p50 (ms) | p95 (ms) | max (ms) | failed bots |
| ------------ | --------------- | --------- | ------------- | -------- | -------- | -------- | ----------- |
| 2 (1)        | TODO(team)      |           |               |          |          |          |             |
| 4 (2)        | TODO(team)      |           |               |          |          |          |             |
| 10 (5)       | TODO(team)      |           |               |          |          |          |             |
| 20 (10)      | TODO(team)      |           |               |          |          |          |             |

### E2 — Localhost vs. remote server (network influence)

Deploy the server to the EC2 instance (docs/AWS_DEPLOY.md) and repeat E1's
`--bots 4 --games 5` from a campus machine.

| Server location | p50 (ms) | p95 (ms) | max (ms) | games/min |
| --------------- | -------- | -------- | -------- | --------- |
| localhost       | TODO(team) |         |          |           |
| EC2 (t4g.small) | TODO(team) |         |          |           |

### E3 — Server resource usage under load (manual sampling)

While an E1 run with 20 bots is in flight, sample the server process every
5 seconds and record the average:

```bash
PID=$(pgrep -f 'onitama.jar server')
while true; do
  ps -o rss=,pcpu= -p $PID; sleep 5
done
```

`rss` is KiB (memory), `pcpu` percent of one core.

| Bots | avg CPU % | avg RSS (MiB) | server threads (see below) |
| ---- | --------- | ------------- | -------------------------- |
| 4    | TODO(team)|               |                            |
| 20   | TODO(team)|               |                            |

Thread count: `ps -o nlwp= -p $PID` (accept thread + client pool + one
match executor per live game + JUL threads).

### E4 (optional) — Elo distribution sanity check

After a 20-bot run the database contains 16+ recorded matches with Elo
updates. Dump and verify the zero-sum property:

```bash
sqlite3 data/onitama.db "SELECT username, elo, wins, losses FROM users ORDER BY elo DESC;"
```

Expected: `SUM(elo)` equals 1000 × user count (Elo is zero-sum; UT06 proves
the math, this proves the wiring).

## How to cite

Report the exact commit hash (`git rev-parse HEAD`), the machine
specifications and all three repetitions per configuration. State clearly
that latencies are client-observed round-trips on the given network path.
