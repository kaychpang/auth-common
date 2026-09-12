# auth-common

Java 21 Spring Boot 3.5 application that authenticates Auth0 JWT bearer tokens from the `Authorization` header and authorizes the authenticated user against a database row.

## How it works

1. Spring Security validates incoming `Authorization: Bearer <jwt>` tokens as an OAuth2 resource server.
2. Auth0 issuer validation is configured with `AUTH0_ISSUER_URI`.
3. Optional audience validation is enabled when `AUTH0_AUDIENCE` is set.
4. `DatabaseUserAuthorizationFilter` reads the user id claim from the JWT, defaulting to `sub`.
5. Requests continue only when `app_users.auth0_user_id` exists and `active = true`; otherwise the response is `403 Forbidden`.
6. The database authorization decision is cached in Redis under `authorization-decisions` so multiple GKE pods share the same cache.
7. Two independent Pub/Sub connections publish and subscribe to string messages in separate GCP projects.

## Configuration

Set these environment variables for a real Auth0 tenant. In GKE, these values should come from `auth-common-config` ConfigMap:

```sh
export AUTH0_ISSUER_URI="https://your-tenant.us.auth0.com/"
export AUTH0_AUDIENCE="https://your-api-identifier"
export AUTH0_USER_ID_CLAIM="sub"
```

Configure PostgreSQL with standard Spring datasource properties or the shorter PostgreSQL variables:

```sh
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/auth_common"
export SPRING_DATASOURCE_USERNAME="auth_common"
export SPRING_DATASOURCE_PASSWORD="change-me"

export POSTGRES_HOST="localhost"
export POSTGRES_PORT="5432"
export POSTGRES_DB="auth_common"
export POSTGRES_USER="auth_common"
export POSTGRES_PASSWORD="change-me"
```

Configure Redis Memorystore. In GKE, these values should come from `auth-common-config` ConfigMap:

```sh
export REDIS_HOST="10.0.0.10"
export REDIS_PORT="6379"
export AUTHORIZATION_CACHE_TTL="5m"
```

## HashiCorp Vault on GKE

Run the application with the `gke` profile to enable Spring Cloud Vault for secrets:

```sh
export SPRING_PROFILES_ACTIVE="gke"
export VAULT_URI="https://vault.example.internal"
export VAULT_KUBERNETES_ROLE="auth-common"
export VAULT_KV_BACKEND="secret"
export VAULT_APPLICATION_NAME="auth-common"
```

With the default KV settings, store secrets at `secret/auth-common`. Use Spring property names as keys so Spring Cloud Vault binds them directly:

```text
spring.datasource.username=auth_common
spring.datasource.password=<database-password>
spring.data.redis.username=<redis-username>
spring.data.redis.password=<redis-password>
auth0.client-id=<auth0-client-id>
auth0.client-secret=<auth0-client-secret>
app.pubsub.project-one.credentials-json=<first-service-account-json>
app.pubsub.project-two.credentials-json=<second-service-account-json>
```

The Kubernetes auth role should be bound to the service account used by the GKE deployment. The GKE profile requires Vault to be available at startup, preventing a pod from silently starting without its credentials.

This application currently validates bearer JWTs as an OAuth2 resource server. That flow uses the Auth0 issuer and audience but does not send the Auth0 client credentials. The Vault-backed `auth0.client-id` and `auth0.client-secret` properties are available for Auth0 Management API or machine-to-machine integration without placing them in the ConfigMap.

## GCP Pub/Sub

Each Pub/Sub project has independent non-secret settings in the ConfigMap:

```text
PUBSUB_PROJECT_ONE_ID
PUBSUB_PROJECT_ONE_TOPIC_ID
PUBSUB_PROJECT_ONE_SUBSCRIPTION_ID
PUBSUB_PROJECT_TWO_ID
PUBSUB_PROJECT_TWO_TOPIC_ID
PUBSUB_PROJECT_TWO_SUBSCRIPTION_ID
```

The credentials JSON values stay in Vault under the keys shown above. When a credential value is empty, the client uses Google Application Default Credentials, which is useful when one GKE Workload Identity has cross-project IAM access.

Publish a string payload from application code:

```java
pubSubEventGateway.publish(PubSubProject.PROJECT_ONE, payload);
pubSubEventGateway.publish(PubSubProject.PROJECT_TWO, payload);
```

By default, subscribed messages are republished inside Spring as `PubSubEvent`. Add a synchronous listener for business processing:

```java
@EventListener
public void onPubSubEvent(PubSubEvent event) {
  // Process event.payload(). Throw on failure to nack and request redelivery.
}
```

All replicas use the same subscription name for each project. Pub/Sub therefore distributes messages between pods rather than delivering every message to every pod. Create a distinct subscription per application when fan-out delivery is required. Pub/Sub remains at-least-once, so handlers should be idempotent.

## GKE ConfigMap

The sample manifests in `k8s/` wire runtime configuration like this:

```text
k8s/configmap.yml     non-secret Auth0, Redis, and PostgreSQL connection settings
k8s/deployment.yml    ConfigMap environment plus Vault access through the pod service account
```

Apply the ConfigMap after replacing placeholder values:

```sh
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/deployment.yml
```

## Run

```sh
mvn spring-boot:run
```

Then call the example protected endpoint:

```sh
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/me
```

## GKE operations

The app exposes Kubernetes-friendly actuator probes:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Prometheus metrics are available at `/actuator/prometheus`.

## Database

Flyway creates this table:

```sql
create table app_users (
    id uuid primary key,
    auth0_user_id varchar(255) not null unique,
    email varchar(320),
    active boolean not null default true,
    created_at timestamp with time zone not null default current_timestamp
);
```

Insert a matching Auth0 subject to authorize a user:

```sql
insert into app_users (id, auth0_user_id, email, active)
values ('018fc31a-1c59-7dd4-a0d8-534dd0f341ab', 'auth0|123456789', 'user@example.com', true);
```