# CockroachDB Driver

This is a driver for CockroachDB. It supports the Postgres JDBC Driver

### Example activity definitions

Run a CockroachDB activity with definitions from activities/cockroachdb-basic.yaml
```
... driver=cockroachdb workload=./driver-cockroachdb/src/main/resources/activities/cockroachdb-basic tags=phase:rampup cycles=10 connectionString=jdbc:postgresql://maxroach@localhost:26257/bank?sslmode=disable showquery=true
```

### CockroachDB ActivityType Parameters

- **connectionString** (Mandatory) - JDBC connection string of the target Cockroach database.

    Example: connectionString=jdbc:postgresql://maxroach@localhost:26257/bank?sslmode=disable
