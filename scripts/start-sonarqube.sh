#!/usr/bin/env bash
# Starts SonarQube Community Edition as a container on the runner machine.
set -euo pipefail

# SonarQube embeds Elasticsearch, which needs a higher memory-map limit than Ubuntu's default
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee /etc/sysctl.d/99-sonarqube.conf > /dev/null

if sudo docker ps -a --format '{{.Names}}' | grep -qx sonar; then
  echo "Container 'sonar' already exists, starting it"
  sudo docker start sonar
else
  sudo docker run -d --name sonar --restart unless-stopped -p 9000:9000 sonarqube:community
fi

echo "SonarQube needs 1 to 2 minutes to start. Watch it with:  sudo docker logs -f sonar"
echo "Then open  http://<EC2_PUBLIC_IP>:9000   (login admin / admin, you will be asked to change the password)"
