# Deployment Documentation

As of writing this, Envision Ad is deployed on an Amazon Linux 2023 EC2 instance using Docker Compose. The deployment process is automated via GitHub Actions, which builds and pushes Docker images whenever changes are pushed to the `main` branch, then deploys them to the EC2 instance.

Images are built in GitHub Actions (not on the EC2 instance) and pushed to GitHub Container Registry (GHCR). The EC2 instance only ever pulls prebuilt images and swaps containers in — it never compiles the frontend or backend itself. This keeps the small EC2 instance's CPU/RAM free during deploys and keeps deploy time to the length of an image pull, not a full build.

### Setup
- EC2 Instance: Amazon Linux 2023 -> Running `docker-compose.prod.yml` (frontend, backend, and reverse-proxy/Nginx services)
- RDS Instance: Amazon RDS for PostgreSQL -> Running the database for the backend service
- Nginx: Reverse proxy running as a Docker container on the EC2 instance (defined as the reverse-proxy service in `docker-compose.prod.yml`) to route traffic from the outer world to the appropriate Docker containers.

---

## Environment Variables

We use Doppler to store and manage environment variables securely. This centralized system ensures that sensitive credentials are never stored in plain text within our source code or on the server.

### Doppler Projects

Our secrets are organized into two primary projects:
* envision-ad-frontend: Stores secrets for the frontend service.
* envision-ad-backend: Stores secrets for the backend service.

### Authentication and Security

Access to these projects from our deployed instances is managed via Service Tokens. These tokens are stored as GitHub Secrets within the repository:
* DOPPLER_FRONTEND_TOKEN
* DOPPLER_BACKEND_TOKEN

Deployed images are pulled from GitHub Container Registry (GHCR), which is private by default. The EC2 instance authenticates each deploy using a GitHub PAT (`read:packages` scope) stored as:
* GHCR_PAT

### To run the project locally with Doppler

1. Install the Doppler CLI by following the instructions at https://docs.doppler.com/docs/install-cli.
2. Authenticate with Doppler using your account credentials:
   ```bash
   doppler login
   ```
3. Run the project using this command:
   ```bash
   doppler run -- docker compose up --build
   ```
4. If you want to run the project using a local .env file, uncomment the following lines in the `docker-compose.yml` file:
   ```yaml
   env_file:
     - .env
   ```
   and
   ```yaml
   env_file:
     - ./frontend/.env
   ```
---

### Infrastructure Setup (Amazon Linux 2023)

The Doppler CLI is required on the EC2 instance to fetch secrets during the deployment process. The following commands were used for the initial configuration:

1. Add the Doppler GPG key:
   ```bash
      sudo rpm --import 'https://packages.doppler.com/public/cli/gpg.DE2A7741A397C129.key'
   ```

2. Add the Doppler repository:
   ```bash
   curl -sLf --retry 3 --tlsv1.2 --proto "=https" 'https://packages.doppler.com/public/cli/config.rpm.txt' | sudo tee /etc/yum.repos.d/doppler-cli.repo
   ```
   
3. Install the CLI using dnf:
   ```bash
   sudo dnf install doppler -y
   ```

---

### Deployment Workflow

`deploy.yml` runs as two jobs:

1. **`build-and-push`** (GitHub-hosted runner) — logs in to `ghcr.io`, then builds both images using the same nested Doppler command as before (so the frontend's `NEXT_PUBLIC_*` build-time values are still injected from Doppler, just in CI instead of on EC2), and pushes them to GHCR tagged with the commit SHA:
   ```bash
   doppler run --project envision-ad-frontend --config prd --token ${{ secrets.DOPPLER_FRONTEND_TOKEN }} -- \
   doppler run --project envision-ad-backend --config prd --token ${{ secrets.DOPPLER_BACKEND_TOKEN }} -- \
   docker compose -f docker-compose.prod.yml build

   docker compose -f docker-compose.prod.yml push
   ```

2. **`deploy`** (SSH into EC2, runs after `build-and-push` succeeds) — logs in to GHCR using a PAT (`GHCR_PAT` secret, `read:packages` scope), then pulls the images the previous job just pushed and swaps containers in. No `--build`, no `--force-recreate` — only the service(s) whose image actually changed get recreated:
   ```bash
   doppler run --project envision-ad-frontend --config prd --token ${{ secrets.DOPPLER_FRONTEND_TOKEN }} -- \
   doppler run --project envision-ad-backend --config prd --token ${{ secrets.DOPPLER_BACKEND_TOKEN }} -- \
   bash -c 'docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d'
   ```

`docker-compose.prod.yml`'s `frontend` and `webservice` services carry both a `build:` block (used only by job 1) and an `image: ghcr.io/${IMAGE_OWNER}/envision-ad-*:${IMAGE_TAG:-latest}` reference (used only by job 2's `pull`/`up`), with `pull_policy: always` so the EC2 instance never silently falls back to building locally.

There are other ways to inject Doppler secrets into Compose. Here is the documentation:
- https://docs.doppler.com/docs/docker-compose

