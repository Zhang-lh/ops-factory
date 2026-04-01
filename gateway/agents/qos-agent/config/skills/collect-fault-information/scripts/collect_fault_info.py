import argparse

import requests
import json
import sys
from requests.auth import HTTPBasicAuth
from load_config import ConfigLoader
from logging_config_collect_fault import logger


class CollectFaultInfo:
    def __init__(self, start_time, end_time, cluster_name, info_type):
        logger.info(f"Initializing CollectFaultInfo, info_type={info_type}")
        self.loader = ConfigLoader()
        self.alarm_url = "/itom/machine/qos/collectFaultInfoStart"
        self.log_url = "/itom/machine/diagnosis/getLogsFileId"
        self.cluster_name = cluster_name
        self.start_time = start_time
        self.end_time = end_time
        self.info_type = info_type
        logger.info(f"Time range: {start_time} - {end_time}, cluster: {cluster_name}")
        logger.info("CollectFaultInfo initialization completed")


    def collect_fault_info(self):
        """
        调用 /itom/machine/qos/collectFaultInfoStart
        POST 接口
        """
        logger.info("Starting to call collect fault information interface")
        logger.info(f"info_type: {self.info_type}")
        try:
            username, password = self.loader.read_auth_from_config()
            logger.info("Successfully read authentication information")
            env_code, mode = self.loader.read_monitor_info()
            logger.info(f"Monitoring info: envCode={env_code}, mode={mode}")
            base_url = self.loader.read_api_info()
            download_url = self.loader.read_download_url()
            logger.info(f"Download URL: {download_url}")

            if self.info_type == "alarm":
                url = f"{base_url.rstrip('/')}{self.alarm_url}"
                logger.info("Using alarm collection interface")
            else:
                url = f"{base_url.rstrip('/')}{self.log_url}"
                logger.info("Using log collection interface")

            headers = {'Content-Type': 'application/json'}

            payload = {
                "envCode": env_code,
                "startTime": self.start_time,
                "endTime": self.end_time,
                "clusterName": self.cluster_name
            }
            logger.info(f"Request payload: {json.dumps(payload, ensure_ascii=False)}")

            logger.info(f"Sending POST request to: {url}")
            response = requests.post(url, json=payload, headers=headers, auth=HTTPBasicAuth(username, password),
                                     verify=False)
            response.raise_for_status()
            logger.info(f"Interface call successful, {response.text}")

            # Parse JSON response to get id
            if response.text is not None:
                # Try to parse as JSON first
                try:
                    response_json = response.json()
                    if isinstance(response_json, dict) and 'id' in response_json:
                        file_id = response_json['id']
                        logger.info("Extracted file_id from JSON response")
                    else:
                        # Response is plain text ID
                        file_id = response.text.strip()
                        logger.info("Extracted file_id from text response")
                    logger.info(f"file_id: {file_id}")
                except json.JSONDecodeError:
                    # Response is plain text ID
                    file_id = response.text.strip()
                    logger.info(f"Response is plain text, file_id: {file_id}")

                # Append id to download_url (download_url is just a path)
                result_url = f"{base_url.rstrip('/')}{download_url.rstrip('/')}{file_id}"
                logger.info(f"生成下载URL: {result_url}")
                return result_url
            else:
                logger.error("Download file_id is None")
                return None
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
        parser.add_argument("--start_time", required=True, type=str, help="Start time")
        parser.add_argument("--end_time", required=True, type=str, help="End time")
        parser.add_argument("--cluster_name", required=True, type=str, help="Cluster name")
        parser.add_argument("--info_type", required=False, type=str, help="Information type, alarm or log", default="log")

        args = parser.parse_args()
        logger.info(f"Command line arguments: start_time={args.start_time}, end_time={args.end_time}")
        logger.info(f"cluster_name={args.cluster_name}, info_type={args.info_type}")

        processor = CollectFaultInfo(args.start_time, args.end_time, args.cluster_name, args.info_type)
        result = processor.collect_fault_info()

        if result is not None:
            logger.info("Interface call successful")
            print(result)
            return result
        else:
            logger.error("Interface call failed")
            sys.exit(1)
    except Exception as e:
        logger.error(f"Program execution error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
