import requests
import json
import argparse
import sys
import configparser
import os
from requests.auth import HTTPBasicAuth
from . import logger


class GetDiagnoseHealthScore:
    ENV_VAR = 'GATEWAY_API_PASSWORD'

    def __init__(self, start_time, end_time, env_code=None):
        logger.info("Initializing GetDiagnoseHealthScore")
        self.config = None
        self.load_config()
        self.start_time = start_time
        self.end_time = end_time
        self.env_code = env_code.strip() if isinstance(env_code, str) and env_code.strip() else None
        logger.info(f"Time range: {start_time} - {end_time}")
        if self.env_code:
            logger.info(f"使用自定义env_code: {self.env_code}")
        logger.info("GetDiagnoseHealthScore initialization completed")


    def post_diagnose_health_score(self):
        """
        调用 /itom/machine/qos/getDiagnoseHealthScore POST 接口
        """
        logger.info("Starting to call diagnose health score interface")
        try:
            username, password = self.read_auth_from_config()
            logger.info("Successfully read authentication information")
            env_code, mode = self.read_monitor_info()
            logger.info(f"Monitoring info: envCode={env_code}, mode={mode}")
            base_url = self.read_api_info()
            url = f"{base_url.rstrip('/')}/itom/machine/qos/getDiagnoseHealthScore"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "startTime": self.start_time,
                "endTime": self.end_time,
                "mode": mode
            }
            logger.info(f"Request payload: {json.dumps(payload, ensure_ascii=False)}")

            logger.info(f"Sending POST request to: {url}")
            response = requests.post(url, json=payload, headers=headers, auth=HTTPBasicAuth(username, password),
                                     verify=False)
            response.raise_for_status()
            logger.info("Interface call successful")
            return response.json()
        except requests.exceptions.RequestException as e:
            logger.error(f"Request exception: {e}")
            return None
        except json.JSONDecodeError as e:
            logger.error(f"JSON parsing failed: {e}")
            return None


    def read_auth_from_config(self):
        """
        从配置文件读取用户名密码
        配置文件格式示例 (config.ini):
        [Auth]
        username = your_username
        password = your_password

        返回 (username, password) 元组，如果缺少则抛出异常
        """
        logger.info("开始读取认证信息")
        try:
            if not self.config.has_section('Auth'):
                error_msg = "配置文件中缺少 [Auth] 节"
                logger.error(error_msg)
                raise KeyError(error_msg)

            username = self.config.get('Auth', 'username', fallback=None)
            password = os.getenv(self.ENV_VAR)
            logger.info(f"Password from environment variable: {'Yes' if password else 'No'}")

            if not username or not password:
                error_msg = "配置文件中缺少 username 或 password 字段"
                logger.error(error_msg)
                raise ValueError(error_msg)

            logger.info("Authentication information read successfully")
            return username, password
        except Exception as e:
            logger.error(f"Failed to read authentication information: {e}")
            raise


    def read_api_info(self):
        logger.info("Starting to read API information")
        try:
            if not self.config.has_section('McpServer'):
                error_msg = "配置文件中缺少 [McpServer] 节"
                logger.error(error_msg)
                raise KeyError(error_msg)

            base_url = self.config.get('McpServer', 'baseUrl', fallback=None)

            if not base_url:
                error_msg = "配置文件中缺少 baseUrl"
                logger.error(error_msg)
                raise ValueError(error_msg)

            logger.info(f"API base URL: {base_url}")
            return base_url
        except Exception as e:
            logger.error(f"Failed to read API information: {e}")
            raise

    def read_monitor_info(self):
        logger.info("Starting to read monitoring information")
        try:
            if not self.config.has_section('Monitor'):
                error_msg = "配置文件中缺少 [Monitor] 节"
                logger.error(error_msg)
                raise KeyError(error_msg)

            env_code = self.config.get('Monitor', 'envCode', fallback=None)
            mode = self.config.get('Monitor', 'mode', fallback=None)

            if self.env_code:
                env_code = self.env_code
                logger.info(f"Using custom env_code: {env_code}")

            if not env_code or not mode:
                error_msg = "配置文件中缺少 env_code 或 mode"
                logger.error(error_msg)
                raise ValueError(error_msg)

            logger.info(f"Monitoring info: envCode={env_code}, mode={mode}")
            return env_code, mode
        except Exception as e:
            logger.error(f"Failed to read monitoring information: {e}")
            raise


    def load_config(self):
        logger.info("开始加载配置文件")
        config_path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "config", "config.ini")
        logger.info(f"配置文件路径: {config_path}")
        if not os.path.exists(config_path):
            error_msg = f"配置文件不存在: {config_path}"
            logger.error(error_msg)
            raise FileNotFoundError(error_msg)
        self.config = configparser.ConfigParser()
        try:
            self.config.read(config_path, encoding='utf-8')
            logger.info("Configuration file loaded successfully")
        except Exception as e:
            error_msg = f"读取配置文件失败: {e}"
            logger.error(error_msg)
            raise RuntimeError(error_msg)


def main():
    logger.info("Program started")
    try:
        parser = argparse.ArgumentParser(description="调用诊断健康评分接口，用户名密码从配置文件读取")
        parser.add_argument("--start_time", required=True, type=int, help="Start timestamp (integer)")
        parser.add_argument("--end_time", required=True, type=int, help="End timestamp (integer)")
        parser.add_argument("--env_code", required=False, type=str, help="Environment code (optional, higher priority than config file)")

        args = parser.parse_args()
        logger.info(f"Command line arguments: start_time={args.start_time}, end_time={args.end_time}")
        if args.env_code:
            logger.info(f"Custom env_code: {args.env_code}")

        processor = GetDiagnoseHealthScore(args.start_time, args.end_time, args.env_code)
        result = processor.post_diagnose_health_score()

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
