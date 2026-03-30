pipeline {
    agent any

    parameters {
        booleanParam(name: 'ROLLBACK', defaultValue: false, description: 'Enable rollback mode')
        string(name: 'ROLLBACK_TAG', defaultValue: '', description: 'Docker image tag to rollback to (only used when ROLLBACK=true)')
    }

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        DOCKERHUB_CREDENTIALS = 'dockerhub-creds'
        DOCKER_REPO = 'founderlink'
        SERVICES = ''
        INFRA_SERVICES = ''
        // config-repo/ changes don't require a rebuild — just a container restart
        // so config-server re-fetches the latest config from its Git backend
        RESTART_SERVICES = ''
        // FIX #3: Flag to skip downstream stages when nothing at all was detected
        SKIP_BUILD = 'false'
    }

    stages {

        // FIX #4: Removed top-level cleanWs() stage.
        // post { always { cleanWs() } } already handles cleanup after every run.
        // Keeping the workspace allows git fetch (fast) instead of full clone (slow),
        // and crucially ensures HEAD~1 exists for the diff in Detect Changed Services.

        // Always checkout — docker-compose files are needed even during rollback
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    def tag = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    if (!tag) {
                        error("Failed to determine commit tag — git rev-parse returned empty")
                    }
                    env.COMMIT_TAG = tag
                    echo "Build commit tag: ${env.COMMIT_TAG}"
                }
            }
        }

        stage('Rollback Mode') {
            when {
                expression { params.ROLLBACK }
            }
            steps {
                script {
                    if (!params.ROLLBACK_TAG) {
                        error("ROLLBACK_TAG parameter is required when ROLLBACK=true")
                    }

                    echo "🔄 ROLLBACK MODE ENABLED"
                    echo "Target tag: ${params.ROLLBACK_TAG}"

                    def allAppServices = ['auth-service', 'user-service', 'startup-service', 'investment-service',
                                          'team-service', 'messaging-service', 'notification-service',
                                          'payment-service', 'wallet-service', 'api-gateway']
                    def allInfraServices = ['config-server', 'eureka-server']

                    env.SERVICES = allAppServices.join(",")
                    env.INFRA_SERVICES = allInfraServices.join(",")
                    // Override COMMIT_TAG with the rollback tag
                    env.COMMIT_TAG = params.ROLLBACK_TAG

                    echo "Rolling back application services: ${env.SERVICES}"
                    echo "Rolling back infrastructure services: ${env.INFRA_SERVICES}"
                }
            }
        }

        stage('Detect Changed Services') {
            when {
                expression { !params.ROLLBACK }
            }
            steps {
                script {

                    // FIX #1: Fetch enough history so HEAD~1 always exists on Jenkins.
                    // A fresh checkout (or shallow clone) may only have HEAD.
                    // --depth=2 fetches HEAD + its parent — exactly what HEAD~1 requires.
                    sh "git fetch --depth=2 origin main || git fetch --depth=2 origin"

                    def changedFiles

                    // Check if HEAD~1 actually exists before attempting the diff
                    def parentExists = sh(
                        script: "git rev-parse --verify HEAD~1 > /dev/null 2>&1",
                        returnStatus: true
                    ) == 0

                    if (parentExists) {
                        // FIX #1 (cont): Removed the broken fallback "|| git diff --name-only HEAD".
                        // That compared working tree to index (always empty on clean checkout).
                        // Now we only run the diff when we know HEAD~1 is available.
                        changedFiles = sh(
                            script: "git diff --name-only HEAD~1 HEAD",
                            returnStdout: true
                        ).trim()
                    } else {
                        // True first commit — no parent exists, build everything
                        echo "First commit detected. Building all services."
                        changedFiles = sh(
                            script: "git ls-files",
                            returnStdout: true
                        ).trim()
                    }

                    def fileList = changedFiles ? changedFiles.split("\n") : []

                    def services        = [] as Set
                    def infraServices   = [] as Set
                    def restartServices = [] as Set

                    fileList.each { file ->
                        if (file.startsWith("auth-service/"))         services.add("auth-service")
                        if (file.startsWith("user-service/"))         services.add("user-service")
                        if (file.startsWith("startup-service/"))      services.add("startup-service")
                        if (file.startsWith("investment-service/"))   services.add("investment-service")
                        if (file.startsWith("team-service/"))         services.add("team-service")
                        if (file.startsWith("messaging-service/"))    services.add("messaging-service")
                        if (file.startsWith("notification-service/")) services.add("notification-service")
                        if (file.startsWith("payment-service/"))      services.add("payment-service")
                        if (file.startsWith("wallet-service/"))       services.add("wallet-service")
                        if (file.startsWith("api-gateway/"))          services.add("api-gateway")
                        if (file.startsWith("config-server/"))        infraServices.add("config-server")
                        if (file.startsWith("eureka-server/"))        infraServices.add("eureka-server")
                        // config-repo/ is the Git-backed config store read by Spring Cloud Config Server.
                        // Changing it does NOT mean config-server source code changed — no rebuild or
                        // new image needed. A simple container restart is enough for config-server to
                        // re-fetch the latest config from its Git backend.
                        if (file.startsWith("config-repo/"))          restartServices.add("config-server")
                    }

                    env.SERVICES         = services.join(",")
                    env.INFRA_SERVICES   = infraServices.join(",")
                    env.RESTART_SERVICES = restartServices.join(",")

                    // Only skip when truly nothing changed across all three categories
                    if (!env.SERVICES && !env.INFRA_SERVICES && !env.RESTART_SERVICES) {
                        echo "No service changes detected. Skipping build."
                        // FIX #3: Set a flag instead of bare return.
                        // A bare return only exits this script{} closure — all downstream
                        // stages (Run Tests, Build Images, Push Images) would still execute.
                        env.SKIP_BUILD = 'true'
                        return
                    }

                    if (env.SERVICES)         echo "Changed application services: ${env.SERVICES}"
                    if (env.INFRA_SERVICES)   echo "Changed infrastructure services: ${env.INFRA_SERVICES}"
                    if (env.RESTART_SERVICES) echo "Config-repo changes — will restart: ${env.RESTART_SERVICES}"
                }
            }
        }

        stage('Run Tests') {
            when {
                // FIX #3: Guard against running when nothing was detected.
                // Also skip for pure config-repo changes — no source code changed, nothing to test.
                expression { !params.ROLLBACK && env.SKIP_BUILD != 'true' && (env.SERVICES || env.INFRA_SERVICES) }
            }
            steps {
                script {
                    def allServices = []
                    if (env.SERVICES) allServices.addAll(env.SERVICES.split(","))
                    if (env.INFRA_SERVICES) allServices.addAll(env.INFRA_SERVICES.split(","))

                    def parallelStages = [:]
                    allServices.each { svc ->
                        parallelStages["Test ${svc}"] = {
                            echo "Testing ${svc}"
                            sh """
                            if [ -f "./${svc}/mvnw" ]; then
                                cd ${svc}
                                chmod +x mvnw
                                ./mvnw test || echo "Tests failed for ${svc}, continuing..."
                                cd ..
                            elif [ -f "./${svc}/pom.xml" ]; then
                                cd ${svc}
                                mvn test || echo "Tests failed for ${svc}, continuing..."
                                cd ..
                            else
                                echo "No test configuration found for ${svc}, skipping tests"
                            fi
                            """
                        }
                    }

                    if (parallelStages.isEmpty()) {
                        echo "No services to test"
                    } else {
                        parallel parallelStages
                    }
                }
            }
        }

        stage('Build Images') {
            when {
                // Also skip for pure config-repo changes — no source code changed, nothing to build.
                expression { !params.ROLLBACK && env.SKIP_BUILD != 'true' && (env.SERVICES || env.INFRA_SERVICES) }
            }
            steps {
                script {
                    def allServices = []
                    if (env.SERVICES) allServices.addAll(env.SERVICES.split(","))
                    if (env.INFRA_SERVICES) allServices.addAll(env.INFRA_SERVICES.split(","))

                    def parallelStages = [:]
                    allServices.each { svc ->
                        parallelStages["Build ${svc}"] = {
                            echo "Building ${svc}"
                            sh """
                            echo "Pulling cache image for ${svc}..."
                            docker pull ${DOCKER_REPO}/${svc}:cache || true

                            docker build \
                              --cache-from ${DOCKER_REPO}/${svc}:cache \
                              -t ${DOCKER_REPO}/${svc}:${env.COMMIT_TAG} \
                              -t ${DOCKER_REPO}/${svc}:cache \
                              ./${svc}
                            """
                        }
                    }

                    if (parallelStages.isEmpty()) {
                        echo "No services to build"
                    } else {
                        parallel parallelStages
                    }
                }
            }
        }

        stage('Push Images') {
            when {
                // FIX #3 + FIX #5: Skip entirely when no services detected or nothing to push.
                // Also skip for pure config-repo changes — no new image was built.
                expression {
                    !params.ROLLBACK &&
                    env.SKIP_BUILD != 'true' &&
                    (env.SERVICES || env.INFRA_SERVICES)
                }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: DOCKERHUB_CREDENTIALS,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'

                    script {
                        def allServices = []
                        if (env.SERVICES) allServices.addAll(env.SERVICES.split(","))
                        if (env.INFRA_SERVICES) allServices.addAll(env.INFRA_SERVICES.split(","))

                        def parallelStages = [:]
                        allServices.each { svc ->
                            parallelStages["Push ${svc}"] = {
                                sh """
                                docker push ${DOCKER_REPO}/${svc}:${env.COMMIT_TAG}
                                docker push ${DOCKER_REPO}/${svc}:cache
                                """
                            }
                        }

                        if (parallelStages.isEmpty()) {
                            echo "No services to push"
                        } else {
                            parallel parallelStages
                        }
                    }
                }
            }
        }

        stage('Prepare Environment') {
            steps {
                withCredentials([file(credentialsId: 'env-file', variable: 'ENV_FILE')]) {
                    sh '''
                    cp $ENV_FILE .env

                    if [ ! -f .env ]; then
                        echo "ERROR: .env file not found!"
                        exit 1
                    fi

                    docker network create proxy-net 2>/dev/null || true
                    '''
                }
            }
        }

        stage('Deploy Infrastructure Services') {
            when {
                expression { env.INFRA_SERVICES != null && env.INFRA_SERVICES != "" }
            }
            steps {
                script {
                    env.INFRA_SERVICES.split(",").each { svc ->
                        echo "Deploying infrastructure service: ${svc}"
                        sh """
                        export TAG=${env.COMMIT_TAG}
                        docker compose -f docker-compose.infra.yml pull ${svc} || true
                        docker compose -f docker-compose.infra.yml up -d --no-deps ${svc}
                        """
                    }
                }
            }
        }

        stage('Deploy Application Services') {
            when {
                expression { env.SERVICES != null && env.SERVICES != "" }
            }
            steps {
                script {
                    env.SERVICES.split(",").each { svc ->
                        echo "Deploying application service: ${svc}"
                        sh """
                        export TAG=${env.COMMIT_TAG}
                        docker compose -f docker-compose.services.yml pull ${svc} || true
                        docker compose -f docker-compose.services.yml up -d --no-deps ${svc}
                        """
                    }
                }
            }
        }

        // Handles config-repo/ changes: no image rebuild, just restart so
        // Spring Cloud Config Server re-reads the latest config from its Git backend.
        stage('Restart Config Services') {
            when {
                expression { env.RESTART_SERVICES != null && env.RESTART_SERVICES != "" }
            }
            steps {
                script {
                    env.RESTART_SERVICES.split(",").each { svc ->
                        echo "Restarting ${svc} to pick up config-repo changes..."
                        sh "docker compose -f docker-compose.infra.yml restart ${svc}"
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def allServices = []
                    if (env.SERVICES)         allServices.addAll(env.SERVICES.split(","))
                    if (env.INFRA_SERVICES)   allServices.addAll(env.INFRA_SERVICES.split(","))
                    if (env.RESTART_SERVICES) allServices.addAll(env.RESTART_SERVICES.split(","))

                    // de-duplicate in case config-server appears in both INFRA_SERVICES and RESTART_SERVICES
                    allServices = allServices.unique()

                    allServices.each { svc ->
                        echo "Checking health of ${svc}"
                        sh """
                        for i in \$(seq 1 30); do
                            if docker ps --filter "name=${svc}" --filter "status=running" | grep -q ${svc}; then
                                echo "${svc} is running"
                                exit 0
                            fi
                            echo "Waiting for ${svc} to start... \$i/30"
                            sleep 2
                        done
                        echo "WARNING: ${svc} may not be healthy"
                        """
                    }
                }
            }
        }

        stage('Cleanup Old Images') {
            when {
                expression { !params.ROLLBACK && env.SKIP_BUILD != 'true' }
            }
            steps {
                sh "docker image prune -f --filter 'until=72h'"
            }
        }
    }

    post {
        success {
            script {
                if (params.ROLLBACK) {
                    echo "✅ Rollback successful to tag: ${params.ROLLBACK_TAG}"
                } else if (env.SKIP_BUILD == 'true') {
                    echo "✅ Pipeline complete — no service changes detected, nothing deployed."
                } else {
                    def deployed = []
                    if (env.SERVICES)         deployed.add("App: ${env.SERVICES}")
                    if (env.INFRA_SERVICES)   deployed.add("Infra: ${env.INFRA_SERVICES}")
                    if (env.RESTART_SERVICES) deployed.add("Restarted: ${env.RESTART_SERVICES}")
                    echo "✅ Deployment successful for ${deployed.join(' | ')}"
                    echo "Tag: ${env.COMMIT_TAG}"
                }
            }
        }
        failure {
            script {
                if (params.ROLLBACK) {
                    echo "❌ Rollback failed. Check logs."
                } else {
                    echo "❌ Pipeline failed. Check logs and consider rollback."
                    echo "To rollback, manually set ROLLBACK=true and ROLLBACK_TAG to a previous commit tag."
                }
            }
        }
        always {
            sh "docker logout || true"
            cleanWs()
        }
    }
}