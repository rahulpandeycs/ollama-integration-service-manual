# ⚾ Player Service

Player Service is a backend application that serves baseball player data. In addition, Player service integrates with [Ollama](https://github.com/ollama/ollama/blob/main/docs/api.md), which allows us to run the [tinyllama LLM]((https://ollama.com/library/tinyllama)) locally.

## Dependencies

- [Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- [maven.apache.org](https://maven.apache.org/install.html)
- Spring Boot 3.3.4 (with Spring Web MVC, Spring Data JPA)
- [H2 Database](https://www.h2database.com/html/main.html)
- [Docker](https://www.docker.com/) or [Podman](https://podman.io/)

## 🛠️ Setup Instructions

1. Verify system dependencies
   1. Java 17
      - Verify installation: `java -version`
   2. Maven
      - Download and install from [maven.apache.org](https://maven.apache.org/install.html)
      - Verify installation, run: `mvn --version`
      - Verify java version linked to maven is Java 17 `Java version: 17.x.x`
   3. Container Manager
      - Download and install from [docker.com](https://www.docker.com/)(recommended) or [podman](https://podman.io/) (alternative)
      - Verify installation, run: `docker --version` for docker

2. Clone this repository or Download the code as zip
   - run `git clone https://github.com/Intuit-A4A/backend-java-player-service.git`

## Run the application

### Part 1: Application Dependencies

1. Install application dependencies
    - From the project's root directory, run: `mvn clean install -DskipTests`

### Part 2: Run Player Service (without LLM)

1. Start the Player service

   ```shell
   mvn spring-boot:run
   ```

2. Verify the Player service is running
      1. Open your browser and visit `http://localhost:8080/v1/players`
      2. If the application is running successfully, you will see player data appear in the browser

### Generate a Similar-Player Team

Start the Python model service on port `5000`, then call the Java endpoint:

```shell
curl --location 'http://localhost:8080/v1/players/aaronha01/team' \
  --header 'Content-Type: application/json' \
  --data '{"teamSize": 5}'
```

The Java service calls the model service at `POST /team/generate`, resolves the returned player IDs, and returns the recommendation metadata together with the full player records.

The model service exposes the active `modelVersion` in the Java response. Train and evaluate the model from `player-service-model` with:

```shell
cd player-service-model
poetry install
poetry run python -m a4a_model.train \
  --input a4a_model/player.csv \
  --model-output a4a_model/team_model.joblib \
  --features-output a4a_model/features_db.csv \
  --metrics-output a4a_model/model_metrics.json
```

The model service also provides `GET /health` and `GET /metrics` for runtime health, latency, error, feedback, and model-version monitoring. See [player-service-model/README.md](player-service-model/README.md) for the model workflow and API examples.

Configure another model-service address with:

```shell
PLAYER_SERVICE_MODEL_BASE_URL=http://localhost:5000 mvn spring-boot:run
```

### Part 3: Start LLM Docker Container

Player service integrates with Ollama 🦙, which allows us to run LLMs locally. This app runs [tinyllama](https://ollama.com/library/tinyllama) model.

- [Ollama API documentation](https://github.com/ollama/ollama/blob/main/docs/api.md)
- [Ollama4J SDK](https://ollama4j.github.io/ollama4j/intro)

1. Pull and run Ollama docker image and download `tinyllama` model
   - Pull Ollama docker image

    ```shell
    docker pull ollama/ollama
    ```

2. Run Ollama docker image on port 11434 as a background process

    ```shell
    docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama
    ```

3. Download and run `tinyllama` model

    ```shell
    docker exec -it ollama ollama run tinyllama
    ```

4. Test Ollama API server

    ```curl
    curl -v --location 'http://localhost:11434/api/generate' --header 'Content-Type: application/json' --data '{"model": "tinyllama","prompt": "why is the sky blue?", "stream": false}'
    ```
Having trouble with docker? Try using podman as an alternative. Instructions [here](https://github.com/Intuit-A4A/backend-java-player-service/wiki/Supplemental-Materials:-Set-up-help#alternative-set-up-instructions)


### Part 4: Verify Player Service and LLM Integration

1. Ensure Player Service is running from previous instructions. If not:

    ```shell
    mvn spring-boot:run
    ```

2. Open your browser and visit `http://localhost:8080/v1/chat/list-models`
   - If the application is running successfully, you will see a json response that include information about tinyllama
