import requests
import json
import sys
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader
from logging_config_root_cause import logger


class GetTopography:
    def __init__(self):
        logger.info("Initializing GetTopography")
        self.loader = ConfigLoader()
        self.url = "/itom/machine/diagnosis/getTopology"
        logger.info("GetTopography initialization completed")


    def post_topography(self):
        """
        调用 /itom/machine/diagnosis/getTopology POST 接口
        """
        logger.info("开始调用拓扑查询接口")
        try:
            username, password = self.loader.read_auth_from_config()
            logger.info("成功读取认证信息")
            env_code, mode = self.loader.read_monitor_info()
            logger.info(f"监控信息: envCode={env_code}")
            base_url = self.loader.read_api_info()
            url = f"{base_url.rstrip('/')}{self.url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
            }
            logger.info(f"请求payload: {json.dumps(payload, ensure_ascii=False)}")

            logger.info(f"发送POST请求到: {url}")
            response = requests.post(url, json=payload, headers=headers, auth=HTTPBasicAuth(username, password),
                                     verify=False)
            response.raise_for_status()
            logger.info("Interface call successful")
            return response.json()
        except requests.exceptions.RequestException as e:
            logger.error(f"请求异常: {e}")
            return None
        except json.JSONDecodeError as e:
            logger.error(f"JSON 解析失败: {e}")
            return None


def main():
    logger.info("Program started")
    try:
        processor = GetTopography()
        result = processor.post_topography()

        if result is not None:
            logger.info("Interface call successful")
            return result
        else:
            logger.error("Interface call failed")
            sys.exit(1)
    except Exception as e:
        logger.error(f"Program execution error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
