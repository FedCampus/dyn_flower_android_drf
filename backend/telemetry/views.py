import logging

from rest_framework import permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import Request
from telemetry.serializers import FitInsTelemetryDataSerializer

logger = logging.getLogger(__name__)


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def fit_ins(request: Request):
    serializer = FitInsTelemetryDataSerializer(data=request.data)  # type: ignore
    if serializer.is_valid():
        print(serializer.validated_data)
        serializer.save()
    else:
        logger.error(serializer.errors)
    return Response("")
