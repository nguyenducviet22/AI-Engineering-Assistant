import axios from "axios";

export const AUTH_REQUIRED_EVENT = "aea.auth-required";

export type UserProfile = {
  id: number;
  email: string;
  fullName: string;
  avatar?: string | null;
  role: "USER" | "ADMIN";
};

export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  user: UserProfile;
};

export type Workspace = {
  id: number;
  name: string;
  description: string;
  language: string;
  framework: string;
  visibility: "PRIVATE" | "SHARED";
};

export type Repository = {
  id: number;
  workspaceId: number;
  repositoryName: string;
  language: string;
  framework: string;
  buildTool: string;
  packageManager: string;
  status: "UPLOADING" | "VALIDATING" | "INDEXING" | "READY" | "FAILED";
  currentVersion: number;
  fileCount: number;
  repositorySize: number;
  failureReason?: string | null;
};

const api = axios.create({
  baseURL: "/api/v1"
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("aea.accessToken");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const url = String(error?.config?.url ?? "");
    if ((status === 401 || status === 403) && !url.startsWith("/auth/")) {
      clearSession();
      window.dispatchEvent(new Event(AUTH_REQUIRED_EVENT));
    }
    return Promise.reject(error);
  }
);

export function saveSession(response: TokenResponse) {
  localStorage.setItem("aea.accessToken", response.accessToken);
  localStorage.setItem("aea.refreshToken", response.refreshToken);
  localStorage.setItem("aea.user", JSON.stringify(response.user));
}

export function clearSession() {
  localStorage.removeItem("aea.accessToken");
  localStorage.removeItem("aea.refreshToken");
  localStorage.removeItem("aea.user");
}

export function currentUser(): UserProfile | null {
  const raw = localStorage.getItem("aea.user");
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as UserProfile;
  } catch {
    clearSession();
    return null;
  }
}

export async function register(payload: { email: string; password: string; fullName: string }) {
  const { data } = await api.post<TokenResponse>("/auth/register", payload);
  saveSession(data);
  return data;
}

export async function login(payload: { email: string; password: string }) {
  const { data } = await api.post<TokenResponse>("/auth/login", payload);
  saveSession(data);
  return data;
}

export async function logout() {
  const refreshToken = localStorage.getItem("aea.refreshToken");
  try {
    if (refreshToken) {
      await api.post("/auth/logout", { refreshToken });
    }
  } finally {
    clearSession();
  }
}

export async function listWorkspaces() {
  const { data } = await api.get<Workspace[]>("/workspaces");
  return data;
}

export async function createWorkspace(payload: Omit<Workspace, "id">) {
  const { data } = await api.post<Workspace>("/workspaces", payload);
  return data;
}

export async function uploadRepository(workspaceId: number, file: File) {
  const form = new FormData();
  form.append("file", file);
  const { data } = await api.post<Repository>(`/workspaces/${workspaceId}/repositories`, form);
  return data;
}

export async function getRepositoryStatus(repositoryId: number) {
  const { data } = await api.get<Pick<Repository, "id" | "status" | "failureReason">>(`/repositories/${repositoryId}/status`);
  return data;
}
