import sys
sys.path.insert(0, "/mnt/e/Dev/Hermes/hermes-agent")
from hermes_cli.config import DEFAULT_CONFIG
print("Default model:", DEFAULT_CONFIG.get("model", "N/A"))
from hermes_cli.auth import PROVIDER_REGISTRY
p = PROVIDER_REGISTRY.get("nvidia")
print("NVIDIA provider base_url:", p.inference_base_url)
print("NVIDIA api_key_env_vars:", p.api_key_env_vars)
# Check what model env vars exist
import os
for k, v in sorted(os.environ.items()):
    if "NVIDIA" in k:
        print(f"  {k}={v}")
