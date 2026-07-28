FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
RUN groupadd --gid 10001 dbapi \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin dbapi
COPY --chown=10001:10001 app.py ./

ENV PYTHONUNBUFFERED=1
EXPOSE 8000
USER 10001:10001
CMD ["gunicorn", "--bind", "0.0.0.0:8000", "--workers", "2", "--threads", "4", "--timeout", "30", "--graceful-timeout", "30", "--access-logfile", "-", "--error-logfile", "-", "app:application"]
