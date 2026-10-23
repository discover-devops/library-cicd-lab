# Library API - GitHub Actions end to end CI/CD lab

A small Spring Boot REST API used to teach a complete CI/CD pipeline:

    git push -> GitHub Actions -> compile -> secret and dependency scan -> unit tests
             -> SonarQube quality gate -> package JAR -> Docker image -> Trivy image scan
             -> Docker Hub -> Amazon EKS

## Repository layout

| Path | What it is |
|---|---|
| `src/` | The application (books API) and its unit tests |
| `pom.xml` | Maven build, JaCoCo coverage |
| `Dockerfile`, `.dockerignore` | Container image (expects `target/library-api.jar`) |
| `k8s/deployment.yaml` | Kubernetes Deployment and LoadBalancer Service |
| `lab-steps/` | The workflow file as it grows, one version per lab topic |
| `scripts/` | Helper scripts: runner tools, SonarQube, EKS cluster, cleanup |
| `.gitleaks.toml` | Gitleaks settings: default rules plus an allowlist for the docs folder |
| `docs/STUDENT-RUNBOOK.md` | Step by step copy and paste runbook for learners |

## How to use the lab

1. Create `.github/workflows/cicd.yaml` and paste `lab-steps/step-01-compile.yaml`.
2. After each topic, replace the file content with the next step file (02, 03, ... 07).
3. `lab-steps/step-07-deploy-final.yaml` is the finished pipeline.

## Values this lab assumes

| Item | Value |
|---|---|
| AWS region | ap-south-1 |
| EKS cluster name | discover-eks |
| Docker Hub image | discoverdevops/library-api |
| GitHub secrets | SONAR_TOKEN, DOCKER_HUB_TOKEN |
| GitHub variables | SONAR_HOST_URL, DOCKER_HUB_USERNAME |

## Run it locally

    mvn test
    mvn spring-boot:run
    curl http://localhost:8080/api/books
