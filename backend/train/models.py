from json import JSONEncoder

from django.db import models
from numpy import ndarray

cfg = {"null": False, "editable": False}


class TrainingDataType(models.Model):
    name = models.CharField(max_length=256, unique=True, **cfg)


# Always change together with Android `db.TFLiteModel.Model`.
class TFLiteModel(models.Model):
    name = models.CharField(max_length=64, unique=True, **cfg)
    n_layers = models.IntegerField(**cfg)
    data_type = models.ForeignKey(
        TrainingDataType, on_delete=models.CASCADE, related_name="tflite_models", **cfg
    )


class TFLiteFile(models.Model):
    path = models.CharField(max_length=64, unique=True, **cfg)
    tflite_model = models.ForeignKey(
        TFLiteModel, on_delete=models.CASCADE, related_name="tflite_files", **cfg
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
        TFLiteModel, on_delete=models.CASCADE, related_name="params", **cfg
    )
