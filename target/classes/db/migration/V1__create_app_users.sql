create table app_users (
    id uuid primary key,
    auth0_user_id varchar(255) not null unique,
    email varchar(320),
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp
);

create index idx_app_users_auth0_user_id_active on app_users (auth0_user_id, active);
