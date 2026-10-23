#!/usr/bin/env bash
# Creates a small EKS cluster. Takes 15 to 20 minutes, so start it before class.
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-discover-eks}"
REGION="${AWS_REGION:-ap-south-1}"

eksctl create cluster \
  --name "$CLUSTER_NAME" \
  --region "$REGION" \
  --nodegroup-name workers \
  --node-type t3.medium \
  --nodes 1

kubectl get nodes
