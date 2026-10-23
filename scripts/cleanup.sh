#!/usr/bin/env bash
# Removes the billable AWS resources created by the lab. Run on the runner machine.
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-discover-eks}"
REGION="${AWS_REGION:-ap-south-1}"

echo "1) Delete the app (this also deletes the AWS load balancer)"
kubectl delete -f k8s/deployment.yaml --ignore-not-found

echo "2) Give AWS a minute to remove the load balancer"
sleep 60

echo "3) Delete the EKS cluster (about 10 minutes)"
eksctl delete cluster --name "$CLUSTER_NAME" --region "$REGION" --wait

echo "Done. Now terminate the EC2 runner instance and delete its key pair and security group."
