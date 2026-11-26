FROM python:3.13-slim@sha256:7c61056e61ac89e852de05f3dc6fa51a6dd2181797bceed46aa725dd7cb2cd3b
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --uid 10001 --create-home appuser
COPY requirements-inference.txt .
RUN pip install --no-cache-dir -r requirements-inference.txt && pip install --no-cache-dir --index-url https://download.pytorch.org/whl/cpu torch==2.14.0+cpu
COPY app ./app
COPY tests ./tests
RUN install -d -o 10001 -g 10001 /training-state/jobs
USER 10001
EXPOSE 8090
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090"]
