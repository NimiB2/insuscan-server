FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y git curl ca-certificates && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

RUN curl -L \
  https://github.com/ChaoningZhang/MobileSAM/raw/master/weights/mobile_sam.pt \
  -o mobile_sam.pt

COPY main.py .

EXPOSE 8001

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]