create table if not exists users (
    id bigserial primary key,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    full_name varchar(255) not null,
    avatar varchar(255),
    role varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists workspaces (
    id bigserial primary key,
    owner_id bigint not null references users(id),
    name varchar(255) not null,
    description varchar(2000),
    language varchar(255) not null,
    framework varchar(255) not null,
    visibility varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists repositories (
    id bigserial primary key,
    workspace_id bigint not null references workspaces(id),
    repository_name varchar(255) not null,
    language varchar(255),
    framework varchar(255),
    build_tool varchar(255),
    package_manager varchar(255),
    status varchar(255) not null,
    current_version integer,
    file_count integer,
    repository_size bigint,
    failure_reason varchar(255),
    created_at timestamp with time zone not null
);

create table if not exists repository_versions (
    id bigserial primary key,
    repository_id bigint not null references repositories(id),
    version integer not null,
    commit_hash varchar(255),
    upload_date timestamp with time zone not null
);

create table if not exists refresh_tokens (
    id bigserial primary key,
    user_id bigint not null references users(id),
    token_hash varchar(255) not null unique,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null
);

create unique index if not exists idx_refresh_token_hash on refresh_tokens(token_hash);
