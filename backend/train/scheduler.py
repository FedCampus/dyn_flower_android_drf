from logging import getLogger
from multiprocessing import Pipe, Process
from multiprocessing.connection import Connection
from threading import Thread

from flwr.common import ndarrays_to_parameters
from train.models import ModelParams, TFLiteModel
from train.run import PORT, flwr_server

logger = getLogger(__name__)


def monitor_db_conn_once(db_conn: Connection):
    kind, msg = db_conn.recv()
    if kind == "done":
        logger.info("DB monitor thread shutting down")
        return True
    elif kind == "save_params":
        if not isinstance(msg, list):
            logger.error(f"Wrong parameters {msg} for `save_params`.")
            return False
        if task is None:
            logger.error(f"Received `save_params` while no running models is found.")
            return False
        to_save = ModelParams(params=msg, tflite_model=task.model)
        try:
            to_save.save()
        except RuntimeError as err:
            logger.error(err)
    return False


def monitor_db_conn(db_conn: Connection):
    while not db_conn.closed:
        try:
            if monitor_db_conn_once(db_conn):
                break
        except RuntimeError as err:
            logger.error(err)
    logger.warning("DB monitor thread exiting.")


def model_params(model: TFLiteModel):
    try:
        params: ModelParams = model.params.last()  # type: ignore
        return ndarrays_to_parameters(params.params)
    except RuntimeError as err:
        logger.warning(err)


TWELVE_HOURS = 12 * 60 * 60


class Server:
    """Spawn a new background Flower server process and monitor it."""

    def __init__(self, model: TFLiteModel) -> None:
        self.model = model
        params = model_params(model)
        db_conn, db_conn1 = Pipe()
        self.process = Process(target=flwr_server, args=(db_conn, params))
        self.process.start()
        self.thread = Thread(target=monitor_db_conn, args=(db_conn1,))
        self.thread.start()
        self.timeout = Thread(target=Process.join, args=(self.process, TWELVE_HOURS))
        self.timeout.start()
        logger.warning(f"Started flower server for model {model}")


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
