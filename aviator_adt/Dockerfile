FROM python:3.13-slim

WORKDIR /app

# Install build essentials and uv
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir uv

COPY pyproject.toml README.md ./
COPY src/ ./src/

RUN uv pip install --system -e .

ENV BIND_ADDRESS=0.0.0.0
ENV BIND_PORT=3000
ENV PYTHONPATH=/app/src

EXPOSE 3000

CMD ["python", "-m", "uvicorn", "aviator.main:app", "--host", "0.0.0.0", "--port", "3000"]
