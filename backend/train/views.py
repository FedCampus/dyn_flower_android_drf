from rest_framework import permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from train.models import TFLiteModel
from train.serializers import TFLiteModelSerializer


# https://www.django-rest-framework.org/api-guide/views/#api_view
@api_view(["GET"])
# https://stackoverflow.com/questions/31335736/cannot-apply-djangomodelpermissions-on-a-view-that-does-not-have-queryset-pro
@permission_classes((permissions.AllowAny,))
def advertise_model(request):
    # TODO: Remove hardcode.
    model = TFLiteModel.objects.first()
    serializer = TFLiteModelSerializer(model)
    return Response(serializer.data)
