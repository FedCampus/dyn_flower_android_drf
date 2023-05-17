from json import JSONEncoder

from django.db import models
from numpy import ndarray


class TFLiteModel(models.Model):
    name = models.CharField(
        max_length=64,
        unique=True,
        null=False,
        editable=False,
    )
    n_layers = models.IntegerField(null=False, editable=False)


class TFLiteFile(models.Model):
    path = models.CharField(max_length=64, unique=True, null=False, editable=False)
    tflite_model = models.ForeignKey(
        TFLiteModel, on_delete=models.CASCADE, related_name="tflite_files"
    )

    def __str__(self) -> str:
        return f"{self.path}"


class NumpyEncoder(JSONEncoder):
    def default(self, obj):
        if isinstance(obj, ndarray):
            return obj.tolist()
        return JSONEncoder.default(self, obj)


class ModelParams(models.Model):
    params = models.JSONField(encoder=NumpyEncoder)
    tflite_model = models.ForeignKey(
        TFLiteModel, on_delete=models.CASCADE, related_name="params"
    )
