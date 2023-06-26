from rest_framework import serializers
from train.models import TFLiteModel


class TFLiteModelSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    name = serializers.CharField()
    file_path = serializers.CharField()
    n_layers = serializers.IntegerField()

    class Meta:
        model = TFLiteModel
        fields = ["id", "name", "file_path", "n_layers"]
