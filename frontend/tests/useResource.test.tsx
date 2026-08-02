import { describe, it, expect, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import { useResource } from "@/app/dashboard/_lib/useResource";

/** Minimal host component: the hook's behaviour is what is under test. */
function Panel({ read, dep }: { read: (dep: number) => Promise<string | null>; dep: number }) {
  const { data, reload, loading } = useResource(`res-${dep}`, () => read(dep), "initial");
  return (
    <div>
      <span data-testid="data">{data}</span>
      <span data-testid="loading">{loading ? "yes" : "no"}</span>
      <button onClick={() => void reload()}>reload</button>
    </div>
  );
}

describe("useResource", () => {
  it("reads once on mount and exposes the value", async () => {
    const read = vi.fn().mockResolvedValue("first");

    render(<Panel read={read} dep={1} />);

    await waitFor(() => expect(screen.getByTestId("data")).toHaveTextContent("first"));
    expect(read).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("no"));
  });

  it("re-reads when what it depends on changes", async () => {
    const read = vi.fn(async (dep: number) => `kit-${dep}`);
    const { rerender } = render(<Panel read={read} dep={1} />);
    await waitFor(() => expect(screen.getByTestId("data")).toHaveTextContent("kit-1"));

    rerender(<Panel read={read} dep={2} />);

    await waitFor(() => expect(screen.getByTestId("data")).toHaveTextContent("kit-2"));
  });

  /**
   * The reason this hook exists rather than seven copies of the same four
   * lines. Every copy would have failed this.
   */
  it("ignores a response that arrives after a newer one", async () => {
    const resolvers: Array<(value: string) => void> = [];
    const read = vi.fn(
      (dep: number) =>
        new Promise<string>((resolve) => {
          resolvers.push((v) => resolve(v));
          void dep;
        })
    );

    const { rerender } = render(<Panel read={read} dep={1} />);
    rerender(<Panel read={read} dep={2} />);
    await waitFor(() => expect(read).toHaveBeenCalledTimes(2));

    // The second read finishes first, then the first one lands late.
    await act(async () => {
      resolvers[1]("kit-2");
    });
    await act(async () => {
      resolvers[0]("kit-1");
    });

    // Without the sequence guard the panel would now be showing the previous
    // kit's data -- someone opens their second kit and reads the first one's.
    expect(screen.getByTestId("data")).toHaveTextContent("kit-2");
  });

  it("keeps the previous value when a read fails", async () => {
    // Deliberate: blanking a table because a background refresh failed is
    // worse than showing data a few seconds old.
    const read = vi
      .fn<(dep: number) => Promise<string | null>>()
      .mockResolvedValueOnce("loaded")
      .mockResolvedValueOnce(null);

    render(<Panel read={read} dep={1} />);
    await waitFor(() => expect(screen.getByTestId("data")).toHaveTextContent("loaded"));

    await act(async () => {
      screen.getByText("reload").click();
    });

    expect(screen.getByTestId("data")).toHaveTextContent("loaded");
  });

  it("does not write into a panel that has been closed", async () => {
    let resolve: (value: string) => void = () => {};
    const read = vi.fn(() => new Promise<string>((r) => { resolve = r; }));
    const errors = vi.spyOn(console, "error").mockImplementation(() => {});

    const { unmount } = render(<Panel read={read} dep={1} />);
    await waitFor(() => expect(read).toHaveBeenCalled());
    unmount();

    await act(async () => {
      resolve("too late");
    });

    expect(errors).not.toHaveBeenCalled();
    errors.mockRestore();
  });
});
