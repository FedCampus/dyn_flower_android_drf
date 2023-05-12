"""`fig_config` and code in `server` are copied from Flower Android example."""
from logging import getLogger
from multiprocessing.connection import Connection

from flwr.server import ServerConfig, start_server
from flwr.server.strategy import FedAvgAndroid

PORT = 8080

logger = getLogger(__name__)


def fit_config(server_round: int):
    """Return training configuration dict for each round.

    Keep batch size fixed at 32, perform two rounds of training with one
    local epoch, increase to two local epochs afterwards.
    """
    config = {
        "batch_size": 32,
        "local_epochs": 5,
    }
    return config


def server():
    # TODO: Make configurable.
    strategy = FedAvgAndroid(
        fraction_fit=1.0,
        fraction_evaluate=1.0,
        min_fit_clients=2,
        min_evaluate_clients=2,
        min_available_clients=2,
        evaluate_fn=None,
        on_fit_config_fn=fit_config,
        initial_parameters=None,
    )

    # Start Flower server for 10 rounds of federated learning
    start_server(
        server_address=f"0.0.0.0:{PORT}",
        config=ServerConfig(num_rounds=10),
        strategy=strategy,
    )


def execute(conn: Connection):
    """Execute one instruction received."""
    kind, msg = conn.recv()
    if kind == "ping":
        logger.warning(f"Received `{msg}`.")
        conn.send("pong")
    elif kind == "server":
        logger.warning(f"Launching server with arguments `{msg}`.")
        # TODO: Use `msg`.
        server()
        conn.send("done")
    else:
        logger.error(f"Unknown kind `{kind}` with message `{msg}`.")
        conn.send("cannot understand")


def run(conn: Connection):
    """Run a procedure that listens to instructions and executes them."""
    logger.warning("Runner started.")
    while True:
        try:
            execute(conn)
        except KeyboardInterrupt:
            break
