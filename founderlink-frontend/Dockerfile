# Stage 1: Build the Angular application
FROM node:20-alpine AS build

WORKDIR /app

# Install dependencies first for better caching
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the application
COPY . .

# Build argument for Razorpay key
ARG RAZORPAY_KEY

# Replace the placeholder in the source code with the actual key
RUN sed -i "s/RAZORPAY_KEY_ID_PLACEHOLDER/$RAZORPAY_KEY/g" src/environments/environment.ts


# Build the application for production
RUN npm run build


# Stage 2: Serve the application with Nginx
FROM nginx:stable-alpine

# Copy the build output from the previous stage
# Angular 17+ application builder outputs to dist/<project-name>/browser
COPY --from=build /app/dist/founderlink-frontend/browser /usr/share/nginx/html

# Copy our custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80 to the internal Docker network
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
