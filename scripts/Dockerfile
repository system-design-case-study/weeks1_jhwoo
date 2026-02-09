FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY load-csv-data.py .

ENTRYPOINT ["python", "-u", "load-csv-data.py"]
