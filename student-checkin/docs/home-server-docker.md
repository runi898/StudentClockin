# Home Server Deployment

This project now uses a true `2 container` backend:

1. `student-checkin-postgres`
2. `student-checkin-api`

No Supabase Kong/Auth/Storage/Edge Runtime containers are required anymore.

## Topology

- LAN / reverse proxy entry:
  - `28547/tcp` -> `student-checkin-api`
- Internal only:
  - `5432/tcp` inside Docker network -> `student-checkin-postgres`

If you want HTTPS on a different public port such as `8443`, put your existing
host `nginx` in front of `http://127.0.0.1:28547`.

## 1. Prepare the server

1. Install Docker Engine and Docker Compose plugin on Ubuntu 22.04+.
2. Create the project folder, for example `/opt/docker/student-checkin-backend`.
3. Copy this repo into that folder.

## 2. Configure environment

From the project root on the server:

```bash
cd /opt/docker/student-checkin-backend
cp deploy/home-server/.env.example deploy/home-server/.env
```

Edit `deploy/home-server/.env`:

```dotenv
POSTGRES_DB=student_checkin
POSTGRES_USER=postgres
POSTGRES_PASSWORD=replace-with-a-strong-db-password
JWT_SECRET=replace-with-a-long-random-secret
API_PORT=28547
```

## 3. Start the 2-container stack

```bash
cd /opt/docker/student-checkin-backend/student-checkin
docker compose \
  --env-file deploy/home-server/.env \
  -f deploy/home-server/docker-compose.two-container.yml \
  up -d --build
```

What happens automatically:

- Postgres starts first
- API container waits for Postgres health
- API container applies all SQL migrations under `supabase/migrations/`
- API container creates the local task-delivery storage directory
- API container runs the Beijing-time daily scheduler internally

## 4. Verify the API

From another LAN machine:

```bash
curl http://YOUR_SERVER_IP:28547/health
curl http://YOUR_SERVER_IP:28547/auth/v1/
curl http://YOUR_SERVER_IP:28547/rest/v1/
```

Expected result:

- all three endpoints return JSON and do not time out

## 5. Point the Android app to this backend

The Android app still reuses the old variable names:

- `STUDENTCLOCKIN_SUPABASE_URL=http://YOUR_SERVER_IP:28547`
- `STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY=student-checkin-public`

`STUDENTCLOCKIN_SUPABASE_PUBLIC_KEY` is now only a placeholder required by the
existing mobile build wiring.

## 6. Optional host nginx reverse proxy

If your public traffic comes through host `nginx`, forward it to local port
`28547`. Example:

```nginx
server {
    listen 8443 ssl;
    server_name your-domain.example;

    ssl_certificate     /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    client_max_body_size 100m;

    location / {
        proxy_pass http://127.0.0.1:28547;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 7. Optional Cloudflare Tunnel

If your home broadband does not expose `80/443`, you can point Cloudflare
Tunnel at:

- `http://127.0.0.1:28547`

That lets the Android app use a fixed HTTPS domain externally while the Docker
stack itself still only listens on the custom local port.

## 8. Reset to a clean new deployment

If you want to completely restart with empty business data:

```bash
cd /opt/docker/student-checkin-backend/student-checkin
docker compose \
  --env-file deploy/home-server/.env \
  -f deploy/home-server/docker-compose.two-container.yml \
  down -v
docker volume prune -f
docker compose \
  --env-file deploy/home-server/.env \
  -f deploy/home-server/docker-compose.two-container.yml \
  up -d --build
```

## Notes

- The product logic assumes `Asia/Shanghai`.
- Daily tasks are generated per Beijing date by the API scheduler.
- Uploaded photo/video/audio files are stored in the API container volume, not in a separate object-storage service.
