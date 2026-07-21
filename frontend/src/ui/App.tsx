import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FolderPlus, LogOut, UploadCloud } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import {
  AUTH_REQUIRED_EVENT,
  clearSession,
  createWorkspace,
  currentUser,
  getRepositoryStatus,
  listWorkspaces,
  login,
  logout,
  register,
  Repository,
  uploadRepository,
  Workspace
} from "../api/client";

const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

export default function App() {
  const [user, setUser] = useState(currentUser());

  useEffect(() => {
    const handleAuthRequired = () => setUser(null);
    window.addEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
    return () => window.removeEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
  }, []);

  if (!user) {
    return <AuthScreen onAuthenticated={(response) => setUser(response.user)} />;
  }

  return <WorkspaceScreen onLogout={() => { clearSession(); setUser(null); }} />;
}

function AuthScreen({ onAuthenticated }: { onAuthenticated: (response: Awaited<ReturnType<typeof login>>) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [error, setError] = useState("");
  const selectMode = (nextMode: "login" | "register") => {
    setMode(nextMode);
    setError("");
    mutation.reset();
  };
  const mutation = useMutation({
    mutationFn: (payload: AuthPayload) => {
      if (payload.mode === "register") {
        return register(payload);
      }
      return login(payload);
    },
    onSuccess: (response) => onAuthenticated(response),
    onError: (err: any) => setError(apiErrorMessage(err, "Authentication failed."))
  });
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    const form = new FormData(event.currentTarget);
    const email = String(form.get("email"));
    const password = String(form.get("password"));

    if (mode === "register") {
      mutation.mutate({
        mode,
        email,
        password,
        fullName: String(form.get("fullName"))
      });
      return;
    }

    mutation.mutate({ mode, email, password });
  };

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <div>
          <p className="eyebrow">Phase 1 Foundation</p>
          <h1>AI Engineering Assistant</h1>
        </div>
        <div className="segmented" aria-label="Authentication mode">
          <button type="button" className={mode === "login" ? "active" : ""} onClick={() => selectMode("login")}>Login</button>
          <button type="button" className={mode === "register" ? "active" : ""} onClick={() => selectMode("register")}>Register</button>
        </div>
        <form onSubmit={handleSubmit} onChange={() => setError("")}>
          {mode === "register" && <Field name="fullName" label="Full name" autoComplete="name" />}
          <Field name="email" label="Email" type="email" autoComplete="email" />
          <Field
            name="password"
            label="Password"
            type="password"
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            minLength={mode === "register" ? 8 : undefined}
          />
          {error && <p className="error">{error}</p>}
          <button className="primary" disabled={mutation.isPending}>{mutation.isPending ? "Working..." : mode === "login" ? "Login" : "Create account"}</button>
        </form>
      </section>
    </main>
  );
}

type AuthPayload =
  | { mode: "login"; email: string; password: string }
  | { mode: "register"; email: string; password: string; fullName: string };

function WorkspaceScreen({ onLogout }: { onLogout: () => void }) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<Workspace | null>(null);
  const [uploaded, setUploaded] = useState<Repository | null>(null);
  const [uploadError, setUploadError] = useState("");
  const workspaces = useQuery({ queryKey: ["workspaces"], queryFn: listWorkspaces });
  const create = useMutation({
    mutationFn: createWorkspace,
    onSuccess: (workspace) => {
      setSelected(workspace);
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    }
  });
  const upload = useMutation({
    mutationFn: ({ workspaceId, file }: UploadPayload) => uploadRepository(workspaceId, file),
    onSuccess: (repository) => {
      setUploadError("");
      setUploaded(repository);
    },
    onError: (err) => setUploadError(apiErrorMessage(err, "Repository upload failed."))
  });
  const handleCreate = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    create.mutate({
      name: String(form.get("name")),
      description: String(form.get("description")),
      language: String(form.get("language")),
      framework: String(form.get("framework")),
      visibility: String(form.get("visibility")) as "PRIVATE" | "SHARED"
    });
  };
  const handleUpload = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selected) {
      return;
    }
    const file = new FormData(event.currentTarget).get("file");
    if (!(file instanceof File)) {
      setUploadError("Choose a ZIP file.");
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setUploadError("ZIP file is too large. Upload a repository ZIP under 50 MB.");
      return;
    }
    setUploadError("");
    upload.mutate({ workspaceId: selected.id, file });
  };
  const status = useQuery({
    queryKey: ["repository-status", uploaded?.id],
    queryFn: () => getRepositoryStatus(uploaded!.id),
    enabled: Boolean(uploaded),
    refetchInterval: (query) => {
      const current = query.state.data?.status;
      return current === "UPLOADING" || current === "VALIDATING" || current === "INDEXING" ? 2500 : false;
    }
  });
  const visibleStatus = status.data?.status ?? uploaded?.status;

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div>
          <p className="eyebrow">Workspace</p>
          <h1>Projects</h1>
        </div>
        <button className="icon-button" title="Logout" onClick={async () => { await logout(); onLogout(); }}>
          <LogOut size={18} />
        </button>
      </aside>
      <section className="workspace-list">
        <form className="compact-form" onSubmit={handleCreate}>
          <Field name="name" label="Name" />
          <Field name="description" label="Description" />
          <Field name="language" label="Language" defaultValue="Java" />
          <Field name="framework" label="Framework" defaultValue="Spring Boot" />
          <label>
            Visibility
            <select name="visibility" defaultValue="PRIVATE">
              <option value="PRIVATE">Private</option>
              <option value="SHARED">Shared</option>
            </select>
          </label>
          <button className="primary" disabled={create.isPending}><FolderPlus size={18} />Create</button>
        </form>
        <div className="list">
          {(workspaces.data ?? []).map((workspace) => (
            <button key={workspace.id} className={selected?.id === workspace.id ? "row selected" : "row"} onClick={() => setSelected(workspace)}>
              <strong>{workspace.name}</strong>
              <span>{workspace.language} / {workspace.framework}</span>
            </button>
          ))}
        </div>
      </section>
      <section className="upload-panel">
        <h2>{selected ? selected.name : "Select a workspace"}</h2>
        <form className="upload-box" onSubmit={handleUpload}>
          <UploadCloud size={28} />
          <input name="file" type="file" accept=".zip,application/zip" />
          <button className="primary" disabled={!selected || upload.isPending}>{upload.isPending ? "Uploading..." : "Upload ZIP"}</button>
        </form>
        {uploadError && <p className="error">{uploadError}</p>}
        {uploaded && (
          <div className="status-strip">
            <strong>{uploaded.repositoryName}</strong>
            <span>{visibleStatus}</span>
            <span>{uploaded.fileCount} files</span>
            <span>{uploaded.language}</span>
            {status.data?.failureReason && <span>{status.data.failureReason}</span>}
          </div>
        )}
      </section>
    </main>
  );
}

type UploadPayload = { workspaceId: number; file: File };

function Field(props: { name: string; label: string; type?: string; autoComplete?: string; defaultValue?: string; minLength?: number }) {
  return (
    <label>
      {props.label}
      <input
        name={props.name}
        type={props.type ?? "text"}
        autoComplete={props.autoComplete}
        defaultValue={props.defaultValue}
        minLength={props.minLength}
        required={props.name !== "description"}
      />
    </label>
  );
}

function apiErrorMessage(error: unknown, fallback: string) {
  const data = (error as any)?.response?.data;
  if (typeof data?.message === "string") {
    return data.message;
  }
  if (typeof data === "string") {
    try {
      const parsed = JSON.parse(data);
      if (typeof parsed?.message === "string") {
        return parsed.message;
      }
    } catch {
      // Fall through to the HTML/fallback handling below.
    }
  }
  if (typeof data === "string" && data.trim().startsWith("<!doctype")) {
    return "Backend API did not return JSON. Please refresh the page and make sure the backend is running.";
  }
  return fallback;
}
