#!/bin/bash
#Program:
#       big health
#History
#2025/05/15     junfenghe.cloud@qq.com  version:0.0.1 init



CONFIG_DIR="conf"
LIB_DIR="lib"
nohup java -cp "BigHealth-1.0-SNAPSHOT.jar:${LIB_DIR}/*" -Dspring.config.location="${CONFIG_DIR}/application.properties" com.bighealth.BigHealthApplication >nohup.out 2>&1 &

echo $! >pid

echo 'big health server has started.'