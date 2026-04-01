import requests
import json
import argparse
import sys
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader
from logging_config_root_cause import logger


class GetAbnormalData:
    def __init__(self, start_time, end_time):
        logger.info(f"Initializing GetAbnormalData with time range: {start_time} - {end_time}")
        self.loader = ConfigLoader()
        self.start_time = start_time
        self.end_time = end_time
        self.url = "/itom/machine/qos/getDiagnoseAbnormalData"
        logger.info("GetAbnormalData initialization completed")


    def post_diagnose_abnormal_data(self):
        """
        调用 /itom/machine/qos/getDiagnoseAbnormalData POST 接口
        """
        logger.info("Starting to call diagnose abnormal data interface")
        try:
            username, password = self.loader.read_auth_from_config()
            logger.info("Successfully read authentication information")
            env_code, mode = self.loader.read_monitor_info()
            logger.info(f"Monitoring info: envCode={env_code}, mode={mode}")
            base_url = self.loader.read_api_info()
            logger.info(f"API base URL: {base_url}")
            url = f"{base_url.rstrip('/')}{self.url}"
            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "startTime": self.start_time,
                "endTime": self.end_time
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


def main():
    logger.info("Program started")
    try:
        parser = argparse.ArgumentParser(description="调用诊断健康评分接口，用户名密码从配置文件读取")
        parser.add_argument("--start_time", required=True, type=int, help="Start timestamp (integer)")
        parser.add_argument("--end_time", required=True, type=int, help="End timestamp (integer)")

        args = parser.parse_args()
        logger.info(f"Command line arguments: start_time={args.start_time}, end_time={args.end_time}")

        processor = GetAbnormalData(args.start_time, args.end_time)
        result = processor.post_diagnose_abnormal_data()

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
