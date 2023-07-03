from rest_framework import serializers
from train.models import TFLiteModel


class TFLiteModelSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    name = serializers.CharField()
    file_path = serializers.CharField()
    layers_sizes = serializers.ListField()

    class Meta:
        model = TFLiteModel
        fields = ["id", "name", "file_path", "layers_sizes"]


# Always change together with Android `HttpClient.PostServerData`.
class PostServerDataSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    start_fresh = serializers.BooleanField(required=False, default=False)  # type: ignore
