from logging import getLogger
from threading import Thread

from flwr.common import Parameters
from helpers import eprint
from telemetry.models import TrainingSession
from train.data import ServerData
from train.models import *
from train.run import PORT, flwr_server

logger = getLogger(__name__)


def model_params(model: TFLiteModel):
    try:
        params: ModelParams = model.params.last()  # type: ignore
        if params is None:
            return
        tensors = [param.tobytes() for param in params.decode_params()]
        return Parameters(tensors, tensor_type="numpy.ndarray")
    except RuntimeError as err:
        logger.warning(err)


TWELVE_HOURS = 12 * 60 * 60


class Task:
    """Spawn a new background Flower server process and monitor it."""

    def __init__(self, model: TFLiteModel, start_fresh: bool) -> None:
        self.model = model
        self.start_fresh = start_fresh
        params = None if start_fresh else model_params(model)
        self.session = TrainingSession(tflite_model=model)
        save_params = lambda params: self.save_params(params)
        self.flwr_server = Thread(target=flwr_server, args=(save_params, params))
        self.flwr_server.start()
        self.timeout = Thread(target=Thread.join, args=(self.flwr_server, TWELVE_HOURS))
        self.timeout.start()
        self.update_session_end_time()
        logger.warning(f"Started flower server for model {model}")

    def update_session_end_time(self):
        self.session.save()

    def save_params(self, params: list[NDArray]):
        to_save = make_model_params(params, self.model)
        eprint(f"Saving parameters for {self.model}.")
        to_save.save()
        self.update_session_end_time()
        eprint(f"Saving parameters for {self.model}.")


task: Task | None = None


def cleanup_task():
    global task
    if task is not None and not task.flwr_server.is_alive():
        task = None


def server(model: TFLiteModel, start_fresh: bool) -> ServerData:
    """Request a Flower server. Return `(status, port)`.
    `status` is "started" if the server is already running,
    "new" if newly started,
    or "occupied" if the background process is unavailable."""
    global task
    cleanup_task()
    if task:
        if task.model == model:
            if start_fresh and not task.start_fresh:
                return ServerData("started_non_fresh", task.session.id, None)
            return ServerData("started", task.session.id, PORT)
        else:
            return ServerData("occupied", None, None)
    else:
        # Start new server.
        task = Task(model, start_fresh)
        return ServerData("new", task.session.id, PORT)
