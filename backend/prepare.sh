#!/bin/bash

# Download TFLite models
mkdir -p /app/static/CIFAR10_model
curl -L https://www.dropbox.com/s/tubgpepk2q6xiny/models.zip?dl=1 -o models.zip
unzip models.zip -d /app/static/CIFAR10_model
rm models.zip

# Create TFLite model in database
python3 /app/manage.py shell <<EOF
from train.models import *
m = TFLiteModel(name="CIFAR10_model", n_layers=10)
m.save()
f = TFLiteFile(path="/static/CIFAR10_model/bottleneck.tflite", tflite_model=m)
f.save()
f = TFLiteFile(path="/static/CIFAR10_model/inference.tflite", tflite_model=m)
f.save()
f = TFLiteFile(path="/static/CIFAR10_model/initialize.tflite", tflite_model=m)
f.save()
f = TFLiteFile(path="/static/CIFAR10_model/optimizer.tflite", tflite_model=m)
f.save()
f = TFLiteFile(path="/static/CIFAR10_model/train_head.tflite", tflite_model=m)
f.save()
EOF