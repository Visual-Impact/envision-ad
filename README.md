# Envision Ad

Envision Ad is a B2B platform developed for **Visual Impact** to bridge the gap between media owners and advertisers. It provides a streamlined marketplace where small businesses can access affordable advertising opportunities on digital screens and physical posters in public spaces.

## 🚀 Overview

Visual Impact’s mission is to democratize advertising. Envision Ad supports this by allowing:
* **Media Owners:** Businesses or individuals with physical or digital ad space to list and monetize their assets.
* **Advertisers:** Small businesses to browse, select, and publish ads in targeted public locations.

## 🛠️ Tech Stack

The project is built using a modern, containerized architecture:

* **Backend:** Java (Spring Boot) webservice.
* **Frontend:** TypeScript-based web application.
* **Infrastructure:** Docker & Docker Compose for orchestration.
* **Web Server:** Nginx for routing and serving content.
* **Secrets Management:** Doppler for secure environment variable handling.

## 📂 Project Structure

* `/webservice`: The Java backend handling business logic and API endpoints.
* `/frontend`: The TypeScript frontend application.
* `/nginx`: Configuration for the Nginx reverse proxy.
* `/docs`: Additional project documentation and diagrams.

## 🚦 Getting Started

### Prerequisites
* Docker and Docker Compose
* Doppler CLI (for environment variables)

### Installation & Running

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/Visual-Impact/envision-ad.git
    cd envision-ad
    ```

2.  **Run with Docker Compose:**
    To start the entire stack (Frontend, Backend, and Nginx):
    ```bash
    docker-compose up
    ```

    For production-specific deployments:
    ```bash
    docker-compose -f docker-compose.prod.yml up
    ```

## 📄 Documentation

For more detailed information, please refer to:
* `deployment.md`: Guide on deploying the application.
* `frontend_architecture.md`: Deep dive into the frontend structure.
* `/docs`: Architectural diagrams and flowcharts.
