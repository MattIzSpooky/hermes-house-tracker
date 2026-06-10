import pytest
import client as client_module


def test_get_client_raises_before_initialization():
    saved = client_module._client
    client_module._client = None
    try:
        with pytest.raises(RuntimeError, match="not initialized"):
            client_module.get_client()
    finally:
        client_module._client = saved
