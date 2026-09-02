-- Cross-instance lock for the scheduled jobs.
--
-- Until now every job was guarded only by an AtomicBoolean in its own service,
-- which answers "is this job already running in THIS JVM". With one instance
-- that is the same question as "is this job already running"; with two it
-- stops being, and the failure is silent -- two instances would each drain the
-- notification outbox, each fold the same analytics rows, each publish the
-- same scheduled kit. Nothing crashes. The work simply happens twice.
--
-- The column shape is ShedLock's own contract, not ours: it reads and writes
-- these four names. Plain SQL types on purpose, so the table is created
-- identically by H2 in PostgreSQL mode and by Neon, and the jobs can be tested
-- without a container.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
