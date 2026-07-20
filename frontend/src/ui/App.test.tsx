import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

vi.mock("../api/client", () => ({
  clearSession: vi.fn(),
  createWorkspace: vi.fn(),
  currentUser: vi.fn(() => null),
  getRepositoryStatus: vi.fn(),
  listWorkspaces: vi.fn(async () => []),
  login: vi.fn(async () => ({
    accessToken: "access",
    refreshToken: "refresh",
    user: {
      id: 3,
      email: "ndv@gmail.com",
      fullName: "vietnd",
      avatar: null,
      role: "USER"
    }
  })),
  logout: vi.fn(),
  register: vi.fn(),
  uploadRepository: vi.fn()
}));

describe("Phase 1 frontend auth", () => {
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("moves to the workspace screen after a successful login response", async () => {
    const host = document.createElement("div");
    document.body.appendChild(host);
    const root = createRoot(host);
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });

    await act(async () => {
      root.render(
        <QueryClientProvider client={client}>
          <App />
        </QueryClientProvider>
      );
    });

    setInput("email", "ndv@gmail.com");
    setInput("password", "Password123");

    await act(async () => {
      host.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });

    expect(host.textContent).toContain("Projects");
    expect(host.textContent).not.toContain("Authentication failed.");
  });
});

function setInput(name: string, value: string) {
  const input = document.querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
  setter?.call(input, value);
  input.dispatchEvent(new Event("input", { bubbles: true }));
}
