import logging
import logging.handlers
import os

# Get logger instance
logger = logging.getLogger(__name__)

# Create logs directory structure
log_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "../..", "..", "..", "logs", "qos-agent")
os.makedirs(log_dir, exist_ok=True)

# Configure log file path
log_file = os.path.join(log_dir, "collect-fault-information.log")

# Create rotating file handler
handler = logging.handlers.RotatingFileHandler(
    log_file, maxBytes=10*1024*1024, backupCount=5, encoding='utf-8'
)

# Create formatter
formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(funcName)s:%(lineno)d - %(message)s')
handler.setFormatter(formatter)

# Add handler to logger
logger.addHandler(handler)

# Set logging level
logger.setLevel(logging.INFO)

def get_logger():
    """Get logger instance for collect-fault-information skill"""
    return logger