#!/usr/bin/env bash
# One-time setup of the Ubuntu EC2 self-hosted runner.
# Run as the normal "ubuntu" user (not root):  bash install-runner-tools.sh
set -euo pipefail

GITLEAKS_VERSION="8.30.1"

echo "[1/8] Base packages"
sudo apt-get update -y
sudo apt-get install -y unzip curl wget gnupg lsb-release ca-certificates jq

echo "[2/8] Maven"
sudo apt-get install -y maven

echo "[3/8] Docker"
sudo apt-get install -y docker.io
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

echo "[4/8] Trivy"
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | gpg --dearmor | sudo tee /usr/share/keyrings/trivy.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/trivy.gpg] https://aquasecurity.github.io/trivy-repo/deb generic main" | sudo tee /etc/apt/sources.list.d/trivy.list > /dev/null
sudo apt-get update -y
sudo apt-get install -y trivy

echo "[5/8] Gitleaks ${GITLEAKS_VERSION}"
curl -sSL -o /tmp/gitleaks.tgz "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/gitleaks_${GITLEAKS_VERSION}_linux_x64.tar.gz"
sudo tar -xzf /tmp/gitleaks.tgz -C /usr/local/bin gitleaks

echo "[6/8] AWS CLI v2"
curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q -o /tmp/awscliv2.zip -d /tmp
sudo /tmp/aws/install --update

echo "[7/8] kubectl"
KUBECTL_VERSION="$(curl -sSL https://dl.k8s.io/release/stable.txt)"
curl -sSL -o /tmp/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 /tmp/kubectl /usr/local/bin/kubectl

echo "[8/8] eksctl"
curl -sSL "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_Linux_amd64.tar.gz" | sudo tar -xz -C /usr/local/bin eksctl

echo
echo "Installed versions:"
mvn -version | head -1
docker --version
trivy --version | head -1
gitleaks version
aws --version
kubectl version --client
eksctl version
echo
echo "IMPORTANT: log out and log back in (new SSH session) so the docker group takes effect,"
echo "then register the GitHub runner from that new session."
