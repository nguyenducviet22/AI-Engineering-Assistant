create extension if not exists vector with schema public;

create table if not exists source_files (
    id bigserial primary key,
    repository_version_id bigint not null references repository_versions(id) on delete cascade,
    file_name varchar(255) not null,
    path varchar(2000) not null,
    language varchar(255) not null,
    size_bytes bigint not null,
    checksum varchar(64) not null
);

create table if not exists repository_status_history (
    id bigserial primary key,
    repository_id bigint not null references repositories(id) on delete cascade,
    status varchar(255) not null,
    reason varchar(255),
    created_at timestamp with time zone not null
);

create table if not exists code_chunks (
    id bigserial primary key,
    source_file_id bigint not null references source_files(id) on delete cascade,
    chunk_index integer not null,
    chunk_type varchar(255) not null,
    content text not null,
    start_line integer not null,
    end_line integer not null,
    workspace_id bigint not null,
    repository_id bigint not null,
    version integer not null,
    package_name varchar(255) not null,
    class_name varchar(255) not null,
    method_name varchar(255) not null,
    language varchar(255) not null,
    framework varchar(255) not null,
    file_path varchar(2000) not null
);

create table if not exists chunk_embeddings (
    id bigserial primary key,
    chunk_id bigint not null references code_chunks(id) on delete cascade,
    model varchar(255) not null,
    dimensions integer not null,
    embedding public.vector(1536) not null,
    created_at timestamp with time zone not null default now()
);

create index if not exists idx_source_files_repository_version on source_files(repository_version_id);
create index if not exists idx_repository_status_history_repository on repository_status_history(repository_id, created_at);
create unique index if not exists idx_source_files_version_path on source_files(repository_version_id, path);
create index if not exists idx_code_chunks_source_file on code_chunks(source_file_id);
create index if not exists idx_code_chunks_metadata on code_chunks(workspace_id, repository_id, version, language);
create index if not exists idx_code_chunks_symbols on code_chunks(package_name, class_name, method_name);
create index if not exists idx_chunk_embeddings_chunk on chunk_embeddings(chunk_id);
create index if not exists idx_chunk_embeddings_vector_cosine on chunk_embeddings using ivfflat (embedding public.vector_cosine_ops) with (lists = 100);
