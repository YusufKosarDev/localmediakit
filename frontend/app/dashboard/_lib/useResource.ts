"use client";

import { useCallback, useEffect, useRef, useState } from "react";

/**
 * A value the dashboard reads from the API and re-reads after it changes it.
 *
 * <p>Every panel had written this out by hand: a piece of state, a useCallback
 * that fetched into it, a useEffect that ran the callback, and a reload after
 * each mutation. Seven copies of four lines is not a crisis, but it is seven
 * places to fix anything that turns out to be wrong with them -- and something
 * was.
 *
 * <p><b>The bug the copies shared.</b> None of them accounted for a response
 * arriving after it stopped being wanted. Switching kits starts a new read
 * while the previous one is still in flight, and if the first finishes last it
 * writes the old kit's rows into the new kit's panel: someone opens their
 * second kit and sees the first one's leads. Nothing distinguished the two
 * responses, so nothing could reject the stale one. Here a sequence number does
 * -- only the newest read may write -- and the same guard drops responses that
 * arrive after the panel is gone.
 *
 * <p><b>Why a key rather than a dependency array.</b> Forwarding the caller's
 * deps into an internal hook is exactly the shape the exhaustive-deps rule
 * cannot see through, and silencing it would have hidden real mistakes at every
 * call site. A key says the same thing more plainly: it is the identity of the
 * thing being read, so `leads-7` and `leads-8` are different resources and
 * changing the string is what asks for a fresh read.
 *
 * <p><b>What is deliberately unchanged.</b> A failed read stays silent and
 * leaves the previous value in place, exactly as the hand-written loaders did.
 * That was a decision, not an oversight: blanking a table because a background
 * refresh failed is worse than showing data a few seconds old. Panels that do
 * want to report a failed read still read the response themselves.
 */
export function useResource<T>(
  key: string,
  read: () => Promise<T | null>,
  initial: T
): {
  data: T;
  reload: () => Promise<void>;
  loading: boolean;
  /**
   * Edits the local copy without going to the server.
   *
   * <p>Some panels let a row be typed into before it is saved, so the fetched
   * value has to be editable in place. That is a real need rather than a leak:
   * the alternative is a second copy of the list in local state and the job of
   * keeping the two in step, which is the bug this hook exists to remove.
   * A later reload overwrites whatever was typed, which is correct -- the
   * server's version won.
   */
  setData: (next: T) => void;
} {
  const [data, setData] = useState<T>(initial);
  const [loading, setLoading] = useState(true);

  /** Which read is current. Anything older than this has lost its claim. */
  const latest = useRef(0);
  const alive = useRef(true);

  // Held in a ref so reload() never goes stale and never needs the caller's
  // closure in a dependency list. Refreshed in an effect rather than during
  // render: a ref written while rendering is a value React is free to discard,
  // and the initial useRef already holds the first read anyway.
  const readRef = useRef(read);
  useEffect(() => {
    readRef.current = read;
  });

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const reload = useCallback(async () => {
    const mine = ++latest.current;
    setLoading(true);
    try {
      const next = await readRef.current();
      // Two guards, not one: `mine` rejects a slower earlier read, `alive`
      // rejects one whose panel has since been closed.
      if (mine !== latest.current || !alive.current) return;
      if (next !== null && next !== undefined) setData(next);
    } finally {
      if (mine === latest.current && alive.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [key, reload]);

  return { data, reload, loading, setData };
}
