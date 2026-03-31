#!/bin/bash

set -e  # ❗ Stop on any error

# ================= CONFIG ================= #
USERNAME="kalyan3003"
TAG=${TAG:-latest}

services=(
  "eureka-server"
  "config-server"
  "api-gateway"
  "auth-service"
  "user-service"
  "startup-service"
  "investment-service"
  "team-service"
  "messaging-service"
  "notification-service"
  "payment-service"
  "wallet-service"
)

echo "🔐 Checking Docker login..."

if ! docker system info 2>/dev/null | grep -q "Username"; then
  echo "⚠️ Unable to verify login automatically."
  echo "👉 If build fails during push, run: docker login"
else
  echo "✅ Docker login detected"
fi

echo "🚀 Starting Build & Push Process..."

for service in "${services[@]}"
do
  echo "----------------------------------------"
  echo "🔨 Building: $service"
  echo "----------------------------------------"

  docker build -t $USERNAME/$service:$TAG ./$service

  echo "----------------------------------------"
  echo "📤 Pushing: $service"
  echo "----------------------------------------"

  docker push $USERNAME/$service:$TAG

  echo "✅ Completed: $service"
done

echo "🎉 All images built and pushed successfully!"