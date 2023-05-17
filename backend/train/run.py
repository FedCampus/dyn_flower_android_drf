"""`fig_config` and code in `server` are copied from Flower Android example."""
from logging import getLogger
from multiprocessing.connection import Connection

from flwr.common import FitRes, Parameters, Scalar
from flwr.server import ServerConfig, start_server
from flwr.server.client_proxy import ClientProxy
from flwr.server.strategy import FedAvgAndroid
from flwr.server.strategy.aggregate import aggregate
from numpy.typing import NDArray

PORT = 8080

logger = getLogger(__name__)


class FedAvgAndroidSave(FedAvgAndroid):
    db_conn: Connection | None = None

    def aggregate_fit(
        self,
        server_round: int,
        results: list[tuple[ClientProxy, FitRes]],
        failures: list[tuple[ClientProxy, FitRes] | BaseException],
    ) -> tuple[Parameters | None, dict[str, Scalar]]:
        """Aggregate fit results using weighted average."""
        # This method is initially copied from `server/strategy/fedavg_android.py`
        # in the `flwr` repository.
        if not results:
            return None, {}
        # Do not aggregate if there are failures and failures are not accepted
        if not self.accept_failures and failures:
            return None, {}
        # Convert results
        weights_results = [
            (self.parameters_to_ndarrays(fit_res.parameters), fit_res.num_examples)
            for client, fit_res in results
        ]
        aggregated = aggregate(weights_results)
        self.signal_save_params(aggregated)
        return self.ndarrays_to_parameters(aggregated), {}

    def signal_save_params(self, params: list[NDArray]):
        if self.db_conn is None:
            # Skip if no connection to DB is provided.
            return
        self.db_conn.send(("save_params", params))


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


def flwr_server(db_conn: Connection | None = None):
    # TODO: Make configurable.
    strategy = FedAvgAndroidSave(
        fraction_fit=1.0,
        fraction_evaluate=1.0,
        min_fit_clients=2,
        min_evaluate_clients=2,
        min_available_clients=2,
        evaluate_fn=None,
        on_fit_config_fn=fit_config,
        initial_parameters=None,
    )
    strategy.db_conn = db_conn

    logger.warning("Starting Flower server.")
    # Start Flower server for 10 rounds of federated learning
    start_server(
        server_address=f"0.0.0.0:{PORT}",
        config=ServerConfig(num_rounds=10),
        strategy=strategy,
    )
    if db_conn is not None:
        db_conn.send(("done", None))
