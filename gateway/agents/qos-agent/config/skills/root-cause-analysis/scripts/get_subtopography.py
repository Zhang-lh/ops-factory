import requests
import json
import sys
import argparse
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader
from logging_config_root_cause import logger


class GetSubTopography:
    def __init__(self, root_alarm, related_alarms=None):
        logger.info("Initializing GetSubTopography")
        logger.info(f"root_alarm类型: {type(root_alarm)}, 是否为None: {root_alarm is None}")
        if related_alarms:
            logger.info(f"related_alarms类型: {type(related_alarms)}")
        self.loader = ConfigLoader()
        self.url = "/itom/machine/diagnosis/getSubTopology"
        self.root_alarm = root_alarm
        self.related_alarms = related_alarms
        logger.info("GetSubTopography初始化完成")


    def post_sub_topography(self):
        """
        调用 /itom/machine/diagnosis/getSubTopology POST 接口
        """
        logger.info("开始调用子拓扑查询接口")
        try:
            username, password = self.loader.read_auth_from_config()
            logger.info("成功读取认证信息")
            env_code, mode = self.loader.read_monitor_info()
            logger.info(f"监控信息: envCode={env_code}, mode={mode}")
            base_url = self.loader.read_api_info()
            url = f"{base_url.rstrip('/')}{self.url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "rootAlarm": self.root_alarm
            }

            if self.related_alarms:
                payload["relatedAlarms"] = self.related_alarms
                logger.info("已添加相关告警信息")

            logger.info(f"请求payload: {json.dumps(payload, ensure_ascii=False)}")

            logger.info(f"发送POST请求到: {url}")
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


def main():
    logger.info("Program started")
    try:
        parser = argparse.ArgumentParser(description="调用子拓扑查询接口")
        parser.add_argument("--root_alarm", required=True, type=str, help="Root cause alarm JSON string")
        parser.add_argument("--related_alarms", required=False, type=str,
                            help="Related alarms JSON array string")

        args = parser.parse_args()
        logger.info(f"Command line arguments: root_alarm={args.root_alarm[:50]}..., related_alarms={args.related_alarms}")

        # 解析 JSON
        try:
            root_alarm = json.loads(args.root_alarm)
            logger.info("root_alarm JSON parsing successful")
        except json.JSONDecodeError as e:
            error_msg = f"root_alarm JSON 解析失败: {e}"
            logger.error(error_msg)
            sys.exit(1)

        related_alarms = None
        if args.related_alarms:
            related_alarms = args.related_alarms
            logger.info("已接收related_alarms参数")

        processor = GetSubTopography(root_alarm, related_alarms)
        result = processor.post_sub_topography()

        if result is not None:
            logger.info(f"Interface call successful. result{json.dumps(result)}")
            return result
        else:
            logger.error("接口调用失败")
            sys.exit(1)
    except Exception as e:
        logger.error(f"程序执行出错: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
