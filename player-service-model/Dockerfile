FROM python:3.11
LABEL authors="efeinstein"

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install \
      --yes \
      --no-install-recommends \
      ca-certificates curl python3 && \
    curl -sSL https://install.python-poetry.org | python3 -
ENV PATH="/root/.local/bin:${PATH}"
COPY . /usr/local/a4a_model
WORKDIR /usr/local/a4a_model
RUN poetry config virtualenvs.create false
RUN poetry install
RUN poetry run python -m a4a_model.train \
      --input a4a_model/player.csv \
      --model-output a4a_model/team_model.joblib \
      --features-output a4a_model/features_db.csv \
      --metrics-output a4a_model/model_metrics.json
WORKDIR /usr/local/a4a_model
EXPOSE 5000
ENTRYPOINT ["python", "-m", "a4a_model.server"]
