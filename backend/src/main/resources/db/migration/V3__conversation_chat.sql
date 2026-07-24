create table if not exists conversations (
    id bigserial primary key,
    workspace_id bigint not null references workspaces(id) on delete cascade,
    title varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists conversation_messages (
    id bigserial primary key,
    conversation_id bigint not null references conversations(id) on delete cascade,
    role varchar(255) not null,
    content text not null,
    citations_json text,
    retrieval_metadata_json text,
    model varchar(255),
    token_usage integer,
    latency_millis bigint,
    created_at timestamp with time zone not null
);

create index if not exists idx_conversations_workspace on conversations(workspace_id, updated_at);
create index if not exists idx_conversation_messages_conversation on conversation_messages(conversation_id, created_at);
