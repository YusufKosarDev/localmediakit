-- Drops the Stripe billing schema along with the code that used it.
--
-- The product is free and always has been: V15 put every account on PRO and
-- nothing in the interface ever called the checkout endpoint. What remained
-- was a payment integration that could not be exercised, its two tables, and
-- the tests that kept them compiling -- code a reader had to understand before
-- discovering it never runs.
--
-- Dropping the tables rather than leaving them empty, because an empty table
-- with foreign keys into users is not free: it is one more thing account
-- deletion has to remember to clear, which is exactly the kind of coupling
-- that outlives the feature that justified it.
--
-- Deliberately not reversible. The rows recorded Stripe test-mode state for a
-- checkout that was never completed by anyone, so there is nothing here worth
-- migrating out. Plan and PlanPolicy stay: they gate live behaviour on every
-- request and are not part of this.
DROP TABLE IF EXISTS processed_stripe_events;
DROP TABLE IF EXISTS subscriptions;
