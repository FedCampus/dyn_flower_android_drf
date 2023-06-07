from logging import getLogger
from multiprocessing import Pipe, Process
from threading import Thread

from flwr.common import Parameters
from numpy import array, single
from telemetry.models import TrainingSession
from train.models import ModelParams, TFLiteModel
from train.run import PORT, flwr_server

logger = getLogger(__name__)


def monitor_db_conn_once(server: "Server"):
    kind, msg = server.db_conn.recv()
    if kind == "done":
        logger.info("DB monitor thread shutting down")
        return True
    elif kind == "save_params":
        if not isinstance(msg, list):
            logger.error(f"Wrong parameters {msg} for `save_params`.")
            return False
        to_save = ModelParams(params=msg, tflite_model=server.model)
        try:
            to_save.save()
            server.update_session_end_time()
        except RuntimeError as err:
            logger.error(err)
    return False


def monitor_db_conn(server: "Server"):
    while not server.db_conn.closed:
        try:
            if monitor_db_conn_once(server):
                break
        except RuntimeError as err:
            logger.error(err)
    server.update_session_end_time()
    logger.warning("DB monitor thread exiting.")


def model_params(model: TFLiteModel):
    try:
        params: ModelParams = model.params.last()  # type: ignore
        if params is None:
            return
        # TODO: Support not just float 32.
        tensors = [array(param, dtype=single).tobytes() for param in params.params]
        return Parameters(tensors, tensor_type="numpy.ndarray")
    except RuntimeError as err:
        logger.warning(err)


TWELVE_HOURS = 12 * 60 * 60


class Server:
    """Spawn a new background Flower server process and monitor it."""

    def __init__(self, model: TFLiteModel) -> None:
        self.model = model
        params = model_params(model)
        db_conn, self.db_conn = Pipe()
        self.session = TrainingSession(tflite_model=model)
        self.process = Process(target=flwr_server, args=(db_conn, params))
        self.process.start()
        self.thread = Thread(target=monitor_db_conn, args=(self,))
        self.thread.start()
        self.timeout = Thread(target=Process.join, args=(self.process, TWELVE_HOURS))
        self.timeout.start()
        self.update_session_end_time()
        logger.warning(f"Started flower server for model {model}")

    def update_session_end_time(self):
        self.session.save()


task: Server | None = None


def cleanup_task():
    global task
    if task is not None and not task.process.is_alive():
        task = None


def server(model: TFLiteModel) -> tuple[str, int | None]:
    """Request a Flower server. Return `(status, port)`.
    `status` is "started" if the server is already running,
    "new" if newly started,
    or "occupied" if the background process is unavailable."""
    global task
    cleanup_task()
    if task:
        if task.model == model:
            return "started", PORT
        else:
            return "occupied", None
    else:
        # Start new server.
        task = Server(model)
        return "new", PORT
