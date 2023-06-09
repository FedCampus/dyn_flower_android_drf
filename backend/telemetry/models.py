from django.db import models
from train.models import TFLiteModel


class TrainingSession(models.Model):
    id: int  # Help static analysis.
    tflite_model = models.ForeignKey(
        TFLiteModel,
        null=False,
        on_delete=models.CASCADE,
        related_name="training_sessions",
    )
    start_time = models.DateTimeField(auto_now_add=True)
    end_time = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"Training session {self.id} for {self.tflite_model} {self.start_time} - {self.end_time}>"


# Always change together with Android `Train.FitInsTelemetryData`.
class FitInsTelemetryData(models.Model):
    id: int  # Help static analysis.
    device_id = models.IntegerField(null=False, editable=False)
    session_id = models.ForeignKey(
        TrainingSession, null=False, on_delete=models.CASCADE, related_name="fit_ins"
    )
    start = models.DateTimeField(null=False)
    end = models.DateTimeField(null=False)

    def __str__(self) -> str:
        return f"FitIns {self.id} on {self.device_id} {self.start} - {self.end}"
