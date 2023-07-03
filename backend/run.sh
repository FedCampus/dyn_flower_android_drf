#!/bin/sh

# run the server
if [ ! -e $PRODUCTION ]; then
    echo "-- Starting development server --"
    python3 /app/manage.py runserver 0.0.0.0:8000
else
    echo "-- Starting production server --"
    uwsgi $PRODUCTION_ARGUMENT --module backend.wsgi
fi

