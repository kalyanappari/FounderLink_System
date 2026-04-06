#!/bin/bash

set -e  # ❗ Stop on any error

# ================= CONFIG ================= #
USERNAME="kalyan3003"
TAG=${TAG:-latest}

# Optional: Build only one service if passed as an argument
if [ -n "$1" ]; then
  services=("$1")
  echo "🎯 Selective build: Only processing $1"
else
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
    "founderlink-frontend"
  )
fi




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

  if [ "$service" == "founderlink-frontend" ]; then
    # Extract RAZORPAY_KEY_ID from .env for the frontend build
    RAZORPAY_KEY=$(grep RAZORPAY_KEY_ID .env | cut -d '=' -f2)
    echo "🔑 Injecting Razorpay Key into frontend..."
    docker build -t $USERNAME/$service:$TAG --build-arg RAZORPAY_KEY=$RAZORPAY_KEY ./$service
  else
    docker build -t $USERNAME/$service:$TAG ./$service
  fi

  echo "----------------------------------------"
  echo "📤 Pushing: $service"
  echo "----------------------------------------"

  docker push $USERNAME/$service:$TAG

  echo "✅ Completed: $service"
done


echo "🎉 All images built and pushed successfully!"