from rest_framework import permissions
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from rest_framework.views import Request


@api_view(["POST"])
@permission_classes((permissions.AllowAny,))
def fit_ins(request: Request):
    id = request.data.get("id")  # type: ignore
    start = request.data.get("start")  # type: ignore
    end = request.data.get("end")  # type: ignore
    print(f"id: {id}, start: {start}, end: {end}")
    return Response("")
