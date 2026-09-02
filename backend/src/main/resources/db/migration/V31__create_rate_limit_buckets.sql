-- Token buckets for the IP throttle, moved out of memory.
--
-- Same failure as the unlock counter and the same reason it is worth fixing:
-- a per-JVM bucket answers "has this IP used its budget against this
-- instance". With two instances the published limit quietly becomes N times
-- what the configuration says, and nothing reports it -- the login throttle
-- that says ten attempts a minute simply allows twenty.
--
-- A token bucket is a token count and the moment it was last topped up. Both
-- live in the row, and the refill is computed in the UPDATE that spends a
-- token, so the read-modify-write is one statement and the database's row lock
-- is what makes concurrent requests serialise.
--
-- Milliseconds since the epoch rather than a timestamp, deliberately: the
-- refill is arithmetic on the elapsed interval, and integer subtraction means
-- the same expression runs unchanged on H2 and on PostgreSQL. Timestamp
-- arithmetic is where those two stop agreeing.
CREATE TABLE rate_limit_buckets (
    bucket_key     VARCHAR(255)     NOT NULL PRIMARY KEY,
    tokens         DOUBLE PRECISION NOT NULL,
    last_refill_ms BIGINT           NOT NULL
);

-- For the sweep only. A bucket nobody has touched in a while is
-- indistinguishable from a full one, so these rows are storage, not state.
CREATE INDEX idx_rate_limit_buckets_age ON rate_limit_buckets (last_refill_ms);
