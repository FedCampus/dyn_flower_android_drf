# Backend Server

## Set up

### Run migrations

```sh
python3 manage.py migrate
```

### Download TFLite models

Download the models to `static/CIFAR10_model`.

Your `static/` should look like this:

```
 static
└──  CIFAR10_model
   ├──  bottleneck.tflite
   ├──  inference.tflite
   ├──  initialize.tflite
   ├──  optimizer.tflite
   └──  train_head.tflite
```

### Create TFLite model in database

Enter a Django shell:

```sh
python3 manage.py shell
```

Add the CIFAR10 model to database by:

```python
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
```

Validate that the models are created:

```python
from train.serializers import *
s = TFLiteModelSerializer(m)
s.data
```

You should get:

```python
{'name': 'CIFAR10_model', 'n_layers': 10, 'tflite_files': ['/static/CIFAR10_model/bottleneck.tflite', '/static/CIFAR10_model/inference.tflite', '/static/CIFAR10_model/initialize.tflite', '/static/CIFAR10_model/optimizer.tflite', '/static/CIFAR10_model/train_head.tflite']}
```

## Development

To test on physical devices in development, run with

```sh
python3 manage.py runserver 0.0.0.0:8000
```

Find you local IP in your system settings for the physical device to connect to.
