# Deployment Documentation

As of writing this, Envision Ad is deployed on an Amazon Linux 2023 EC2 instance using Docker Compose. The deployment process is automated via GitHub Actions, which builds and deploys the Docker containers whenever changes are pushed to the `main` branch.

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
4. If you want to run the project using a local .env file, add the following lines in the `docker-compose.yml` file under the appropriate services:
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

The GitHub deployment action uses a nested command structure to inject environment variables from both Doppler projects into the Docker containers at runtime.

#### Option 1: Inject Doppler token through github secrets 
Deployment Command:
```bash
doppler run --project envision-ad-frontend --config prd --token ${{ secrets.DOPPLER_FRONTEND_TOKEN }} -- \
doppler run --project envision-ad-backend --config prd --token ${{ secrets.DOPPLER_BACKEND_TOKEN }} -- \
docker compose -f docker-compose.prod.yml up -d --build --force-recreate
```

#### Option 2: Login to Doppler and then run the same command as locally
This will give you a url that you can open on your machine and paste the provided code to authenticate the EC2 instance on Doppler
```bash
doppler login
```
After logging in, make sure the correct Doppler project and config are selected for this environment. You can either:

- Run `doppler setup` once on the instance and choose the appropriate project and config when prompted, then use the shorter form:
  ```bash
  doppler run -- docker compose up --build
There are other ways to achieve this. Here is the documentation:
- https://docs.doppler.com/docs/docker-compose


---

### New Instance Setup

To set up a new EC2 instance for deployment, follow these steps:
1. Launch a new Amazon Linux 2023 EC2 instance.
2. SSH into the instance
   ```bash
   ssh -i /path/to/your-key.pem ec2-user@your-ec2-instance-public-dns
   ```
3. Install the Doppler CLI by following the steps outlined in the "Infrastructure Setup" section above.
4. Install Docker (Docker Compose V2 is included as a plugin on Amazon Linux 2023):
   ```bash
   sudo dnf update -y
   sudo dnf install docker -y
   sudo systemctl start docker
   sudo systemctl enable docker
   # Verify that the Docker Compose V2 plugin is available
   sudo docker compose version
   By default, the ec2-user cannot run docker commands without sudo. To make your GitHub Actions or manual Doppler commands work seamlessly, add the user to the docker group.
   ```bash
   sudo usermod -aG docker ec2-user
   # Note: You must log out and back in for this to take effect, 
   # or run: newgrp docker

   - Amazon Linux 2023 often lacks the Docker Buildx plugin by default. Without it, your docker compose ... --build command will likely fail with a "requires buildx" error.

5. Create the plugin directory
   ```bash
   sudo mkdir -p /usr/libexec/docker/cli-plugins
   ```
   Download Buildx
   ```bash
   sudo curl -L https://github.com/docker/buildx/releases/download/v0.19.3/buildx-v0.19.3.linux-amd64 -o /usr/libexec/docker/cli-plugins/docker-buildx
   ```
   Make it executable
   ```bash
   sudo chmod +x /usr/libexec/docker/cli-plugins/docker-buildx
   ```

   If you are running a very small instance (e.g., t2.micro), you might run into memory issues during the build process. To mitigate this, you can create a swap file:
   ```bash
   # Create 2GB Swap File to prevent build crashes
   sudo fallocate -l 2G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   ```
6. Install Git:
   ```bash
   sudo dnf install git -y
   ```
7. Clone the repository:
   ```bash
      git clone https://github.com/anvsult/envision-ad.git
   ```
8. Navigate to the project directory:
   ```bash
   cd envision-ad
   ```
9. Run the deployment command:
   ```bash
   doppler run -- docker compose -f docker-compose.prod.yml up -d --build --force-recreate
   ```

### To get the reverse proxy to work with a custom domain:

1. Install Certbot:
   ```bash
   sudo dnf install certbot -y
   ```
2. Obtain SSL certificates:
   ```bash
   sudo certbot certonly --standalone -d envision-ad.ca -d www.envision-ad.ca
   ```
3. Download the recommended SSL options for Nginx:
   ```bash
   sudo curl -s https://raw.githubusercontent.com/certbot/certbot/master/certbot-nginx/certbot_nginx/_internal/tls_configs/options-ssl-nginx.conf -o /etc/letsencrypt/options-ssl-nginx.conf
   ```bash
   sudo openssl dhparam -out /etc/letsencrypt/ssl-dhparams.pem 2048
   ```
