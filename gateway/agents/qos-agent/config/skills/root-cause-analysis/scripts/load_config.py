import configparser
import os
from . import logger


class ConfigLoader:
    ENV_VAR = 'GATEWAY_API_PASSWORD'

    def __init__(self):
        logger.info("Initializing ConfigLoader")
        self.config = None
        self.load_config()
        logger.info("ConfigLoader initialization completed")

    def read_auth_from_config(self):
        """
        从配置文件读取用户名密码
        配置文件格式示例 (config.ini):
        [Auth]
        username = your_username
        返回 (username, password) 元组，如果缺少则抛出异常
        """
        logger.info("Starting to read authentication information")
        try:
            if not self.config.has_section('Auth'):
                error_msg = "配置文件中缺少 [Auth] 节"
                logger.error(error_msg)
                raise KeyError(error_msg)

            username = self.config.get('Auth', 'username', fallback=None)
            password = os.getenv(self.ENV_VAR)
            logger.info(f"从环境变量获取到密码: {'是' if password else '否'}")

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

            logger.info(f"API基础URL: {base_url}")
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

            if not env_code or not mode:
                error_msg = "配置文件中缺少 env_code 或 mode"
                logger.error(error_msg)
                raise ValueError(error_msg)

            logger.info(f"监控信息: envCode={env_code}, mode={mode}")
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