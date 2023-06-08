FROM python:3.11.3-slim-bullseye
COPY ./ /app/

RUN python3 -m pip install -r /app/requirements.txt && ls -la /app

RUN python3 /app/manage.py migrate

RUN sh /prepare.sh

CMD python3 /app/manage.py runserver 0.0.0.0:8000
