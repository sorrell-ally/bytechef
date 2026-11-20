# Deploying a trial instance

This is a runbook for an automation agent or a script. It builds this fork from source on one Linux machine and runs
it with Docker Compose next to PostgreSQL, with the Community Edition features (SSO and project Git sync) switched on.

It must be built from source: the published `bytechef/bytechef` images do not contain this fork's changes.

> **Status:** these steps are assembled from the repo's own build script and config, but have not yet been run end to
> end. The first run is also the first full build and first startup of the SSO and Git sync commits. If a step fails,
> stop and report the failing command and its output; do not improvise around it.

> **Licence:** the image built here still contains the modules under `server/ee/`, which are covered by the ByteChef
> Enterprise licence (`server/ee/LICENSE`). That licence allows development and testing. Do not use this image for
> production without a subscription or a build that leaves `server/ee/` out.

## 1. Machine requirements

| Item | Requirement |
|---|---|
| OS | Linux, x86_64 or arm64 |
| Memory | 16 GB (the Gradle build alone uses a 4 GB heap plus workers; 8 GB machines get OOM-killed) |
| Disk | 30 GB free |
| Network | Outbound HTTPS to Maven Central, npm, Docker Hub, `ghcr.io`, GitHub |
| Inbound | TCP 8080 (or a reverse proxy in front of it) |

Software, all on `PATH`:

- `git`
- Docker Engine with the Compose plugin (`docker compose version` works) and a user allowed to run `docker`
- JDK 25 (Temurin 25 is what the project is developed on)
- Node.js 24 or newer, with `npm`
- `curl`, `openssl`

Check before continuing; every command must succeed:

```bash
git --version && docker compose version && java -version && node --version && npm --version
```

## 2. Inputs

Set these once. Only `PUBLIC_URL` normally needs changing.

```bash
export REPO_URL=https://github.com/sorrell-ally/bytechef.git
export REPO_REF=master
export APP_DIR=/opt/bytechef-trial
export IMAGE_TAG=trial
# The URL people will type into a browser. SSO redirect URIs and webhook URLs are derived from it.
export PUBLIC_URL=http://$(hostname -f):8080
```

## 3. Get the source

```bash
mkdir -p "$APP_DIR"
if [ -d "$APP_DIR/src/.git" ]; then
    git -C "$APP_DIR/src" fetch origin "$REPO_REF"
    git -C "$APP_DIR/src" checkout "$REPO_REF"
    git -C "$APP_DIR/src" reset --hard "origin/$REPO_REF"
else
    git clone --branch "$REPO_REF" "$REPO_URL" "$APP_DIR/src"
fi
git -C "$APP_DIR/src" rev-parse HEAD
```

Record the printed commit hash in the deploy log.

## 4. Build the image

The repo's own script builds the server jar, the client bundle and both Docker images. `--no-push` keeps the images in
the local Docker image store and builds only for this machine's architecture.

```bash
cd "$APP_DIR/src"
./docker-build.sh --no-push "$IMAGE_TAG"
```

- Expect 20 to 60 minutes on a first run; it also runs the server-app checks and tests.
- Success ends with `Built ... images into the local docker image store`.
- Result: `bytechef/bytechef:$IMAGE_TAG` (server plus client). Confirm with
  `docker image inspect bytechef/bytechef:$IMAGE_TAG >/dev/null`.
- On a strange Gradle failure (`NoClassDefFoundError`, `zip file closed`), run `./gradlew --stop` and repeat the
  command once before reporting it.

## 5. Write the runtime configuration

Secrets are generated once and kept in `$APP_DIR/.env`. Do not regenerate them on redeploys: the database password is
baked into the PostgreSQL volume, and changing the remember-me key logs everyone out.

```bash
cd "$APP_DIR"
if [ ! -f .env ]; then
    umask 077
    cat > .env <<EOF
IMAGE_TAG=$IMAGE_TAG
PUBLIC_URL=$PUBLIC_URL
POSTGRES_PASSWORD=$(openssl rand -hex 24)
REMEMBER_ME_KEY=$(openssl rand -hex 32)
EOF
fi

cat > docker-compose.yml <<'EOF'
services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
      - POSTGRES_DB=bytechef
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "postgres", "-d", "bytechef"]
      interval: 5s
      timeout: 5s
      retries: 20
  bytechef:
    image: bytechef/bytechef:${IMAGE_TAG}
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      - BYTECHEF_DATASOURCE_URL=jdbc:postgresql://postgres:5432/bytechef
      - BYTECHEF_DATASOURCE_USERNAME=postgres
      - BYTECHEF_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - BYTECHEF_SECURITY_REMEMBER_ME_KEY=${REMEMBER_ME_KEY}
      - BYTECHEF_PUBLIC_URL=${PUBLIC_URL}
      # The SSO and Git sync features in this fork only activate in the Community Edition.
      - BYTECHEF_EDITION=CE
      - BYTECHEF_SECURITY_SSO_ENABLED=true
      # Uncomment to let Git sync reach a Git server on a private network address.
      # - BYTECHEF_PROJECT_GIT_SYNC_ALLOW_PRIVATE_NETWORK_HOSTS=true
    volumes:
      # Holds the generated encryption key. Losing it makes stored connection secrets unreadable.
      - bytechef_home:/root/.bytechef
    ports:
      - "8080:8080"
volumes:
  postgres_data:
  bytechef_home:
EOF
```

## 6. Start

```bash
cd "$APP_DIR"
docker compose up -d
```

Database migrations run automatically on startup.

## 7. Verify

Wait for the health endpoint, up to 10 minutes (first start runs every migration):

```bash
for attempt in $(seq 1 120); do
    if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
        echo "healthy"; break
    fi
    sleep 5
done
curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || {
    docker compose logs --tail=200 bytechef
    exit 1
}
```

Then check, and report each result:

1. `curl -fsS -o /dev/null -w '%{http_code}\n' http://localhost:8080/login` prints `200`.
2. `docker compose logs bytechef | grep -ciE 'liquibase.*(error|exception)'` prints `0`.
3. `docker compose exec postgres psql -U postgres -d bytechef -c '\dt sso_provider' -c '\dt project_git_sync'` lists
   both tables. A missing table means the edition or the feature flag did not take effect.
4. `curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{"email":"a@example.com"}' http://localhost:8080/api/sso/discover`
   prints anything other than `404`. (`403` is fine: it is the CSRF check, which proves the endpoint exists.)

A deploy is successful only when the health check and all four checks pass.

## 8. Hand over to a person

These need a browser and outside accounts, so automation stops here:

- Open `$PUBLIC_URL` and create the first account through the sign-up page.
- **SSO:** Settings → Single Sign-On. Microsoft Entra ID setup is in `server/libs/platform/platform-sso/README.md`.
  Entra requires an `https://` redirect URI for anything except `localhost`, so put a TLS reverse proxy in front and
  set `PUBLIC_URL` to the `https://` address before testing a real login.
- **Git sync:** open a project → Git Sync. It needs an HTTPS repository URL and an access token.

## 9. Redeploy, logs, removal

```bash
# Redeploy the latest commit: repeat sections 3 and 4, then
cd "$APP_DIR" && docker compose up -d

# Logs
docker compose logs -f bytechef

# Stop, keeping data
docker compose down

# Remove everything including the database and the encryption key (irreversible)
docker compose down -v
```

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Build killed, exit 137 or 143 | Not enough memory; use a 16 GB machine |
| `Node.js ... is not supported for building the client` | Node older than 24 |
| `bytechef` container restarts in a loop | Read `docker compose logs bytechef`; usually a failed migration or a Spring bean conflict. Report the first stack trace |
| No "Single Sign-On" in settings, no "Git Sync" on projects | `BYTECHEF_EDITION` is not `CE`, or the signed-in user is not an admin |
| SSO settings page says the feature is disabled | `BYTECHEF_SECURITY_SSO_ENABLED` is not `true` |
| Git sync rejects the repository URL | Not HTTPS, or a private address without the allow-private-hosts setting |
