-- The product became fully free: every feature is open to everyone. Pull all
-- existing FREE accounts up to PRO. New accounts default to PRO in code
-- (see User constructor). The FREE tier and its PlanPolicy gating stay in the
-- schema and code so paid plans can be reintroduced later without a rewrite.
UPDATE users SET plan = 'PRO' WHERE plan = 'FREE';
